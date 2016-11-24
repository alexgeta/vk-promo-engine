package com.vk.promoengine.logic;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.vk.api.exceptions.VkApiException;
import com.vk.promoengine.entities.Campaign;
import com.vk.promoengine.entities.Proxy;
import com.vk.promoengine.entities.VkAccount;
import com.vk.promoengine.exceptions.PromotingException;
import com.vk.promoengine.intf.StopCallBack;
import com.vk.promoengine.tasks.CheckAccountTask;
import com.vk.promoengine.tasks.CheckProxyTask;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class PromoManager {

    private static final Logger log = Logger.getLogger(PromoManager.class);
    private static PromoManager instance;
    private final Map<Long, CampaignRunDescriptor> activeCampaigns = new HashMap<>();
    private String antigateKey = "2e0442f05bdfb231d25c8289f1d3ad26";
    private SessionFactory sessionFactory;

    private PromoManager() {
        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder().configure().build();
        try {
            sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    public static PromoManager getInstance() {
        if (instance == null) {
            instance = new PromoManager();
        }
        return instance;
    }

    public synchronized <R> R handleCampaign(long campaignId, CampaignHandler<R> handler) {
        Session session = sessionFactory.openSession();
        log.debug(String.format("Session %d created for handling campaign %d", session.hashCode(), campaignId));
        Transaction transaction = session.beginTransaction();
        Campaign campaign = session.load(Campaign.class, campaignId);
        try {
            return handler.handle(campaign);
        } finally {
            session.flush();
            transaction.commit();
            session.close();
            log.debug(String.format("Session %d flushed and closed.", session.hashCode()));
        }
    }

    public synchronized Campaign createCampaign(Campaign newCampaign) {
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();
        newCampaign.setId(System.currentTimeMillis());
        Date now = new Date();
        newCampaign.setLastStartTime(now);
        newCampaign.setLastStopTime(now);
        session.save(newCampaign);
        session.flush();
        transaction.commit();
        session.close();
        return newCampaign;
    }

    public synchronized void deleteCampaign(long campaignId) {
        Session session = sessionFactory.openSession();
        session.beginTransaction();
        Campaign campaign = session.load(Campaign.class, campaignId);
        session.delete(campaign);
        session.getTransaction().commit();
        session.close();
    }

    public synchronized Campaign getCampaign(long campaignId) {
        CampaignRunDescriptor runDescriptor = activeCampaigns.get(campaignId);
        if (runDescriptor != null && runDescriptor.getCampaign() != null) {
            return runDescriptor.getCampaign();
        }
        Session session = sessionFactory.openSession();
        Campaign campaign = session.get(Campaign.class, campaignId);
        session.close();
        return campaign;
    }

    public synchronized Campaign startCampaign(final long campaignId) {
        log.debug(String.format("Run starting campaign %d ...", campaignId));
        final Session session = sessionFactory.openSession();
        session.beginTransaction();
        log.debug("Session " + session.hashCode() + " created for campaign " + campaignId);
        try {
            final Campaign campaign = session.load(Campaign.class, campaignId);
            final List<Proxy> proxies = prepareProxies(campaign.getProxies());
            final List<VkAccount> accounts = prepareAccounts(campaign.getAccounts(), proxies);
            final List<VkPromoter> promoters = createPromoters(campaign, accounts, proxies);
            if (promoters.isEmpty()) {
                String msg = "No one promoter has been created.";
                log.debug(msg);
                throw new PromotingException(msg);
            }
            log.debug(String.format("%d promoters has been created for campaign %s", promoters.size(), campaign.getId()));
            runRecovery(campaign, promoters, session);
            campaign.setLastStartTime(new Date());
            session.getTransaction().commit();
            session.beginTransaction();
            ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("promoters-%d").setDaemon(true).build();
            ExecutorService executorService = Executors.newFixedThreadPool(promoters.size(), threadFactory);
            promoters.forEach(executorService::submit);
            activeCampaigns.put(campaignId, new CampaignRunDescriptor(campaign, promoters, executorService, session));
            return campaign;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            session.getTransaction().commit();
            session.close();
            throw e;
        }
    }

    private List<VkAccount> prepareAccounts(List<VkAccount> accounts, List<Proxy> proxies) {
        log.debug("Preparing accounts...");
        List<VkAccount> validAccounts = getValidAccounts(accounts, proxies);
        if (validAccounts.isEmpty()) {
            throw new PromotingException("There are no one valid account.");
        }
        log.debug(String.format("Prepared %d accounts", validAccounts.size()));
        return validAccounts;
    }

    private List<Proxy> prepareProxies(List<Proxy> proxies) {
        log.debug("Preparing proxies...");
        List<Proxy> validProxies = getValidProxies(proxies);
        if (validProxies.isEmpty()) {
            throw new PromotingException("There are no one valid proxy.");
        }
        log.debug(String.format("Prepared %d valid proxies.", validProxies.size()));
        return validProxies;
    }

    private List<VkPromoter> createPromoters(final Campaign campaign, List<VkAccount> accounts, List<Proxy> proxies) {
        log.debug(String.format("Creating promoters for campaign %s ...", campaign.getId()));
        final List<VkPromoter> result = new ArrayList<>();
        PromotedUsersContainer promotedUsers = new PromotedUsersContainer(campaign.getPromotedUsers());
        TargetUsersContainer targetUsers = new TargetUsersContainer(campaign.getTargetUsers());
        int accNumber = 0;
        for (VkAccount account : accounts) {
            accNumber++;
            Proxy proxy = getProxy(accNumber, accounts.size(), proxies);
            String accessToken = account.getAccessToken();
            try {
                String promoterName = account.getFullName() + ":" + account.getUsername();
                VkPromoter vkPromoter = new VkPromoter(createPromoterArgs(campaign, accessToken, proxy, promotedUsers, targetUsers));
                log.debug(String.format("Promoter %s created using proxy %s", promoterName, proxy.getHost()));
                result.add(vkPromoter);
            } catch (VkApiException e) {
                log.error(e.getMessage() + " errorCode " + e.getErrorCode() + ". " + account.getFullName() + ":" + account.getUsername());
                if (e.getErrorCode() == 5) {
                    account.setStatus(VkAccount.Status.SUSPENDED);
                }
            } catch (IOException e) {
                log.error("Failed to create promoter: " + e.getMessage(), e);
            }
        }
        return result;
    }

    private VkPromoterArgs createPromoterArgs(Campaign campaign, String accessToken, Proxy proxy, PromotedUsersContainer promotedUsers, TargetUsersContainer targetUsers) {
        return new VkPromoterArgs(
                campaign,
                accessToken,
                proxy,
                antigateKey,
                promotedUsers,
                targetUsers
        );
    }

    private List<VkAccount> getValidAccounts(List<VkAccount> accounts, List<Proxy> validProxies) {
        final int accsCount = accounts.size();
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("accountCheckers-%d").setDaemon(true).build();
        ExecutorService executorService = Executors.newFixedThreadPool(accsCount, threadFactory);
        List<Future<VkAccount>> futures = new ArrayList<>();
        int accNumber = 0;
        for (VkAccount account : accounts) {
            accNumber++;
            Proxy proxy = getProxy(accNumber, accsCount, validProxies);
            Future<VkAccount> future = executorService.submit(new CheckAccountTask(account, proxy));
            futures.add(future);
        }
        executorService.shutdown();
        return collectResults(futures);
    }

    private <T> List<T> collectResults(List<Future<T>> futures) {
        List<T> result = new ArrayList<>();
        for (Future<T> future : futures) {
            try {
                T resultValue = future.get();
                if (resultValue != null) {
                    result.add(resultValue);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error(e.getMessage(), e);
            }
        }
        return result;
    }

    private Proxy getProxy(int accNumber, int accsCount, List<Proxy> proxies) {
        int proxiesCount = proxies.size();
        int accsPerProxy = accsCount / proxiesCount;
        int proxyIndex = accNumber / accsPerProxy;
        if (proxyIndex >= proxiesCount) {
            proxyIndex = proxiesCount - 1;
        }
        return proxies.get(proxyIndex);
    }

    private List<Proxy> getValidProxies(List<Proxy> proxies) {
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("proxyCheckers-%d").setDaemon(true).build();
        ExecutorService executorService = Executors.newFixedThreadPool(proxies.size(), threadFactory);
        List<Future<Proxy>> futures = new ArrayList<>();
        proxies.forEach(proxy -> futures.add(executorService.submit(new CheckProxyTask(proxy))));
        executorService.shutdown();
        return collectResults(futures);
    }

    private void runRecovery(Campaign campaign, List<VkPromoter> promoters, Session session) {
        Date lastStartTime = campaign.getLastStartTime();
        Date lastStopTime = campaign.getLastStopTime();
        if (lastStartTime.getTime() > lastStopTime.getTime()) {
            log.debug("Run recovery for campaign ID " + campaign.getId() + " ...");
            try {
                for (VkPromoter vkPromoter : promoters) {
                    vkPromoter.recover(lastStartTime);
                    session.flush();
                }
            } catch (VkApiException e) {
                throw new PromotingException(e);
            }
            log.debug("Recovery finished.");
        }
    }

    public synchronized void stopCampaign(final long campaignId, final StopCallBack stopCallBack) {
        log.debug("Stop request for campaign " + campaignId + " received!");
        final CampaignRunDescriptor runDescriptor = activeCampaigns.get(campaignId);
        final Campaign campaign = runDescriptor.getCampaign();
        final List<VkPromoter> promoters = runDescriptor.getPromoters();
        final Session session = runDescriptor.getSession();
        ExecutorService executorService = runDescriptor.getExecutorService();
        promoters.forEach(VkPromoter::stop);
        executorService.shutdown();
        try {
            log.debug("Await termination executor service...");
            if (!executorService.awaitTermination(5, TimeUnit.MINUTES)) {
                log.warn("Forcibly shutdown executor service...");
                executorService.shutdownNow();
            }
            log.debug("Executor service is terminated.");
            campaign.setLastStopTime(new Date());
            activeCampaigns.remove(campaignId);
            if (stopCallBack != null) {
                stopCallBack.onStop(null);
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        } finally {
            session.getTransaction().commit();
            session.close();
            log.debug(String.format("Session %d for campaign %d committed and closed.", session.hashCode(), campaignId));
        }
    }

    public synchronized List<Campaign> listCampaigns() {
        final Session session = sessionFactory.openSession();
        List list = session.createCriteria(Campaign.class)
                /*.setFetchMode("promotedUsers", FetchMode.JOIN)
                .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)*/
                .list();
        session.close();
        return list;
    }

    public synchronized boolean isCampaignRunning(Long id) {
        CampaignRunDescriptor descriptor = activeCampaigns.get(id);
        List<VkPromoter> activePromoters = null;
        if (descriptor != null) {
            activePromoters = descriptor.getPromoters();
        }
        return activePromoters != null && !activePromoters.isEmpty();
    }

}
