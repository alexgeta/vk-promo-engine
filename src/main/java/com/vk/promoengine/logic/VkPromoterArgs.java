package com.vk.promoengine.logic;

import com.vk.promoengine.entities.Campaign;
import com.vk.promoengine.entities.Proxy;

public class VkPromoterArgs {

    private Campaign campaign;
    private String accessToken;
    private Proxy proxy;
    private String antigateKey;
    private PromotedUsersContainer promotedUsers;
    private TargetUsersContainer targetUsers;

    public VkPromoterArgs(Campaign campaign,
                          String accessToken,
                          Proxy proxy,
                          String antigateKey,
                          PromotedUsersContainer promotedUsers,
                          TargetUsersContainer targetUsers) {

        this.campaign = campaign;
        this.accessToken = accessToken;
        this.proxy = proxy;
        this.antigateKey = antigateKey;
        this.promotedUsers = promotedUsers;
        this.targetUsers = targetUsers;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public String getAntigateKey() {
        return antigateKey;
    }

    public PromotedUsersContainer getPromotedUsers() {
        return promotedUsers;
    }

    public TargetUsersContainer getTargetUsers() {
        return targetUsers;
    }
}
