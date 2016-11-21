package com.vk.promoengine.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.Date;

@Entity
public class PromoAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Type actionType;
    private Date actionTime;
    private String description;

    public PromoAction(Type actionType) {
        this.actionType = actionType;
    }

    public PromoAction(Type actionType, Date actionTime) {
        this(actionType, actionTime, null);
    }

    public PromoAction(Type actionType, Date actionTime, String description) {
        this.actionType = actionType;
        this.actionTime = actionTime;
        this.description = description;
    }

    public PromoAction() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Type getActionType() {
        return actionType;
    }

    public void setActionType(Type actionType) {
        this.actionType = actionType;
    }

    public Date getActionTime() {
        return actionTime;
    }

    public void setActionTime(Date actionTime) {
        this.actionTime = actionTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PromoAction that = (PromoAction) o;
        return actionType == that.actionType;
    }

    @Override
    public int hashCode() {
        return actionType.hashCode();
    }

    public enum Type {
        SEND_WELCOME_MSG, SEND_PROMO_MSG, SEND_FRIEND_REQUEST, SEND_GROUP_INVITE, IGNORE
    }
}
