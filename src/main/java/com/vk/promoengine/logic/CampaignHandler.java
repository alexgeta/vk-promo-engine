package com.vk.promoengine.logic;

import com.vk.promoengine.entities.Campaign;

public interface CampaignHandler<T> {
    T handle(Campaign campaign);
}
