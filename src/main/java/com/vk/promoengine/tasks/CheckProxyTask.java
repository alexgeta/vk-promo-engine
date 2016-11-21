package com.vk.promoengine.tasks;

import com.vk.promoengine.entities.Proxy;
import com.vk.promoengine.logic.ProxyValidator;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.Callable;

public class CheckProxyTask implements Callable<Proxy> {

    private static final Logger log = Logger.getLogger(CheckProxyTask.class);
    private final Proxy proxy;

    public CheckProxyTask(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public Proxy call() throws Exception {
        ProxyValidator proxyValidator = new ProxyValidator();
        try {
            return proxyValidator.validate(proxy);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
