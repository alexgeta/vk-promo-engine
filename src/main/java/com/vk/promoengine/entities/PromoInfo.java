package com.vk.promoengine.entities;

import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import javax.persistence.*;
import java.util.List;

@Entity
public class PromoInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ElementCollection()
    @LazyCollection(LazyCollectionOption.FALSE)
    private List<String> welcomeMessages;
    @ElementCollection()
    @LazyCollection(LazyCollectionOption.FALSE)
    private List<String> promoMessages;
    private String groupId;
    private Integer promotedUsersLimit;

    public PromoInfo(List<String> welcomeMessages, List<String> promoMessages, String groupId, Integer promotedUsersLimit) {
        this.welcomeMessages = welcomeMessages;
        this.promoMessages = promoMessages;
        this.groupId = groupId;
        this.promotedUsersLimit = promotedUsersLimit;
    }

    public PromoInfo() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<String> getWelcomeMessages() {
        return welcomeMessages;
    }

    public void setWelcomeMessages(List<String> welcomeMessages) {
        this.welcomeMessages = welcomeMessages;
    }

    public List<String> getPromoMessages() {
        return promoMessages;
    }

    public void setPromoMessages(List<String> promoMessages) {
        this.promoMessages = promoMessages;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Integer getPromotedUsersLimit() {
        return promotedUsersLimit;
    }

    public void setPromotedUsersLimit(Integer targetUsersCount) {
        this.promotedUsersLimit = targetUsersCount;
    }
}
