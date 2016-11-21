package com.vk.promoengine.logic;

import com.vk.promoengine.entities.PromoAction;
import com.vk.promoengine.entities.PromotedUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PromotedUsersContainer {

    private final List<PromotedUser> promotedUsers;
    private final Map<String, PromotedUser> promotedUsersMap;

    public PromotedUsersContainer(List<PromotedUser> promotedUsers) {
        this.promotedUsers = promotedUsers;
        this.promotedUsersMap = mapPromotedUsers(promotedUsers);
    }

    private Map<String, PromotedUser> mapPromotedUsers(List<PromotedUser> promotedUsers) {
        Map<String, PromotedUser> idUserMap = new HashMap<>();
        for (PromotedUser promotedUser : promotedUsers) {
            idUserMap.put(promotedUser.getVkId(), promotedUser);
        }
        return idUserMap;
    }

    public synchronized PromotedUser get(String userId) {
        return promotedUsersMap.get(userId);
    }

    public synchronized void add(PromotedUser promotedUser) {
        promotedUsers.add(promotedUser);
        promotedUsersMap.put(promotedUser.getVkId(), promotedUser);
    }

    public synchronized boolean isActionPerformed(String userId, PromoAction.Type actionType) {
        PromotedUser promotedUser = promotedUsersMap.get(userId);
        return promotedUser != null && promotedUser.isActionPerformed(actionType);
    }

    public synchronized boolean contains(String userId) {
        return promotedUsersMap.containsKey(userId);
    }

    public synchronized List<PromotedUser> getPromotedByType(PromoAction.Type promoActionType) {
        return promotedUsers.stream().filter(
                promotedUser -> promotedUser.isActionPerformed(promoActionType)).collect(Collectors.toList());
    }
}
