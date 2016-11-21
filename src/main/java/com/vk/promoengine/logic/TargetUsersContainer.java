package com.vk.promoengine.logic;


import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TargetUsersContainer {

    private final List<String> targetUsers;
    private final Set<String> targetUsersSet = new HashSet<>();

    public TargetUsersContainer(List<String> targetUsers) {
        this.targetUsers = targetUsers;
        targetUsersSet.addAll(targetUsers);
    }

    public synchronized void add(String userId) {
        targetUsers.add(userId);
        targetUsersSet.add(userId);
    }

    public synchronized String getOne() {
        String result = null;
        while (StringUtils.isBlank(result)) {
            result = targetUsers.remove(0);
        }
        targetUsersSet.remove(result);
        return result;
    }

    public synchronized boolean remove(String userId) {
        return targetUsersSet.remove(userId) && targetUsers.remove(userId);
    }
}
