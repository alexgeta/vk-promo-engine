package com.vk.promoengine.exceptions;


public class PromotingException extends RuntimeException {
    public PromotingException(String message) {
        super(message);
    }

    public PromotingException(Throwable cause) {
        super(cause);
    }
}
