package com.vk.promoengine.tasks;

import com.vk.api.VkApi;
import com.vk.api.exceptions.VkApiException;
import com.vk.promoengine.entities.Proxy;
import com.vk.promoengine.entities.VkAccount;
import com.vk.promoengine.exceptions.PromotingException;
import com.vk.promoengine.logic.AccessTokenDiscoverer;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.Callable;

public class CheckAccountTask implements Callable<VkAccount> {

    private static final Logger log = Logger.getLogger(CheckAccountTask.class);
    private final VkAccount account;
    private final Proxy proxy;

    public CheckAccountTask(VkAccount account, Proxy proxy) {
        this.account = account;
        this.proxy = proxy;
    }

    @Override
    public VkAccount call() throws Exception {
        try {
            if (StringUtils.isBlank(account.getAccessToken())) {
                String accessToken = resolveAccessToken(account, proxy);
                account.setAccessToken(accessToken);
                account.setStatus(VkAccount.Status.ACTIVE);
                updateExtendedInfo(account, proxy);
            }
            if (!VkAccount.Status.SUSPENDED.equals(account.getStatus())) {
                return account;
            }
        } catch (IOException | VkApiException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private void updateExtendedInfo(VkAccount account, Proxy proxy) throws VkApiException {
        VkApi vkApi = new VkApi(account.getAccessToken(), convertProxy(proxy));
        account.setFullName(vkApi.getFullName());
        account.setVkId(vkApi.getMyId());
    }

    private com.vk.api.entitites.Proxy convertProxy(Proxy proxy) {
        return new com.vk.api.entitites.Proxy(
                proxy.getHost(), proxy.getPort(), proxy.getLogin(), proxy.getPassword(), proxy.getScheme());
    }

    private String resolveAccessToken(VkAccount account, Proxy proxy) throws IOException {
        String username = account.getUsername();
        String password = account.getPassword();
        AccessTokenDiscoverer tokenDiscoverer = new AccessTokenDiscoverer(proxy);
        String accessToken = tokenDiscoverer.retrieveToken(username, password);
        if (StringUtils.isBlank(accessToken)) {
            throw new PromotingException(String.format("Cannot resolve access token for '%s:%s'", username, password));
        }
        return accessToken;
    }
}
