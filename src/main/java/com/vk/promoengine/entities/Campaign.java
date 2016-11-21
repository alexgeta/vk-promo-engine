package com.vk.promoengine.entities;

import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
public class Campaign {

    @Id()
    private Long id;

    private String name;

    private Date lastStartTime;

    private Date lastStopTime;

    @OneToOne(cascade = CascadeType.ALL)
    private PromoInfo promoInfo;

    @ElementCollection
    @OrderColumn
    private List<String> targetUsers;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "campaign")
    @LazyCollection(LazyCollectionOption.TRUE)
    private List<PromotedUser> promotedUsers = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL)
    @LazyCollection(LazyCollectionOption.FALSE)
    private List<VkAccount> accounts;

    @OneToMany(cascade = CascadeType.ALL)
    @LazyCollection(LazyCollectionOption.FALSE)
    private List<Proxy> proxies;

    public Campaign(String name, PromoInfo promoInfo, List<String> targetUsers, List<VkAccount> accounts, List<Proxy> proxies) {
        this.name = name;
        this.promoInfo = promoInfo;
        this.targetUsers = targetUsers;
        this.accounts = accounts;
        this.proxies = proxies;
    }

    public Campaign(Long id) {
        this.id = id;
    }

    public Campaign() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getLastStartTime() {
        return lastStartTime;
    }

    public void setLastStartTime(Date lastStartTime) {
        this.lastStartTime = lastStartTime;
    }

    public Date getLastStopTime() {
        return lastStopTime;
    }

    public void setLastStopTime(Date lastStopTime) {
        this.lastStopTime = lastStopTime;
    }

    public PromoInfo getPromoInfo() {
        return promoInfo;
    }

    public void setPromoInfo(PromoInfo promoInfo) {
        this.promoInfo = promoInfo;
    }

    public List<String> getTargetUsers() {
        return targetUsers;
    }

    public void setTargetUsers(List<String> targetUsers) {
        this.targetUsers = targetUsers;
    }

    public List<PromotedUser> getPromotedUsers() {
        return promotedUsers;
    }

    public void setPromotedUsers(List<PromotedUser> promotedUsers) {
        this.promotedUsers = promotedUsers;
    }

    public List<VkAccount> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<VkAccount> usedAccounts) {
        this.accounts = usedAccounts;
    }

    public List<Proxy> getProxies() {
        return proxies;
    }

    public void setProxies(List<Proxy> proxies) {
        this.proxies = proxies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Campaign)) return false;
        Campaign campaign = (Campaign) o;
        return getId().equals(campaign.getId());//for Hibernate lazy load only
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

}
