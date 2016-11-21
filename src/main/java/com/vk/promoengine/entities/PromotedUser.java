package com.vk.promoengine.entities;

import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class PromotedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String vkId;
    private String promoterId;
    @ManyToOne(cascade = CascadeType.ALL)
    private Campaign campaign;
    @OneToMany(cascade = CascadeType.ALL)
    @LazyCollection(LazyCollectionOption.FALSE)
    private Set<PromoAction> performedActions;
    @Column(columnDefinition = "text")
    private String messageLog;

    public PromotedUser(String vkId, String promoterId, Campaign campaign, Set<PromoAction> performedActions) {
        this.vkId = vkId;
        this.promoterId = promoterId;
        this.campaign = campaign;
        this.performedActions = performedActions;
    }

    public PromotedUser(String vkId, Campaign campaign) {
        this.vkId = vkId;
        this.campaign = campaign;
    }

    public PromotedUser() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVkId() {
        return vkId;
    }

    public void setVkId(String vkId) {
        this.vkId = vkId;
    }

    public String getPromoterId() {
        return promoterId;
    }

    public void setPromoterId(String promoterId) {
        this.promoterId = promoterId;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }

    public Set<PromoAction> getPerformedActions() {
        return performedActions;
    }

    public void setPerformedActions(Set<PromoAction> performedActions) {
        this.performedActions = performedActions;
    }

    public String getMessageLog() {
        return messageLog;
    }

    public void setMessageLog(String messageLog) {
        this.messageLog = messageLog;
    }

    public boolean isActionPerformed(PromoAction.Type actionType) {
        Set<PromoAction.Type> actionTypes = getActionTypes(performedActions);
        return actionTypes.contains(actionType);
    }

    private Set<PromoAction.Type> getActionTypes(Set<PromoAction> promoActions) {
        Set<PromoAction.Type> actionTypes = new HashSet<>();
        if (promoActions != null) {
            actionTypes.addAll(promoActions.stream().map(PromoAction::getActionType).collect(Collectors.toList()));
        }
        return actionTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PromotedUser)) return false;
        PromotedUser that = (PromotedUser) o;
        return getCampaign().equals(that.getCampaign()) && getVkId().equals(that.getVkId());// for Hibernate
    }

    @Override
    public int hashCode() {
        int result = getId().hashCode();
        result = 31 * result + getCampaign().hashCode();
        return result;
    }
}
