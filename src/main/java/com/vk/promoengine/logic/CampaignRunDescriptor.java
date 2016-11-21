package com.vk.promoengine.logic;


import com.vk.promoengine.entities.Campaign;
import org.hibernate.Session;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class CampaignRunDescriptor {

    private Campaign campaign;
    private List<VkPromoter> promoters;
    private ExecutorService executorService;
    private Session session;

    public CampaignRunDescriptor(Campaign campaign, List<VkPromoter> promoters, ExecutorService executorService, Session session) {
        this.campaign = campaign;
        this.promoters = promoters;
        this.executorService = executorService;
        this.session = session;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public List<VkPromoter> getPromoters() {
        return promoters;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public Session getSession() {
        return session;
    }
}
