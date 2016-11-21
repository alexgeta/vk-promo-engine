package com.vk.promoengine.logic;

import com.vk.promoengine.entities.Proxy;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ProxyValidator {


    private static final Logger log = Logger.getLogger(ProxyValidator.class);
    private static String HTTP = "http";
    private static String SOCKS = "socks";

    public Proxy validate(Proxy proxy) throws IOException {
        if (isHttpProxyValid(proxy)) {
            proxy.setScheme(HTTP);
        } else if (isSocksProxyValid(proxy)) {
            proxy.setScheme(SOCKS);
        } else return null;
        return proxy;
    }

    private boolean isHttpProxyValid(Proxy proxy) throws IOException {
        CredentialsProvider credsProvider = buildCredsProvider(proxy);
        CloseableHttpClient httpclient = HttpClients.createDefault();
        if (credsProvider != null) {
            httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        }
        try {
            HttpHost target = new HttpHost("httpbin.org", 80, "http");
            HttpHost proxyHost = new HttpHost(proxy.getHost(), proxy.getPort(), "http");
            RequestConfig config = RequestConfig.custom().setProxy(proxyHost).build();
            HttpGet request = new HttpGet("/ip");
            request.setConfig(config);
            CloseableHttpResponse response = httpclient.execute(target, request);
            int statusCode = response.getStatusLine().getStatusCode();
            String responseString = EntityUtils.toString(response.getEntity());
            if (statusCode != 200) {
                log.warn(String.format("Proxy %s:%s:%s:%s Status code=" + statusCode + "; response: " + responseString,
                        proxy.getHost(), proxy.getPort(), proxy.getLogin(), proxy.getPassword()));
            }
            boolean isValid = statusCode == 200 && responseString.contains(proxy.getHost());
            response.close();
            return isValid;
        } catch (IOException ignore) {
            ignore.printStackTrace();
        } finally {
            httpclient.close();
        }
        return false;
    }

    private boolean isSocksProxyValid(Proxy proxy) throws IOException {
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", new MyConnectionSocketFactory(SSLContexts.createSystemDefault()))
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
        CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();
        try {
            InetSocketAddress socksAddr = new InetSocketAddress(proxy.getHost(), proxy.getPort());
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksAddr);
            HttpHost target = new HttpHost("httpbin.org", 80, "http");
            HttpGet request = new HttpGet("/api");
            CloseableHttpResponse response = httpclient.execute(target, request, context);
            try {
                boolean isValid = response.getStatusLine().getStatusCode() == 200 &&
                        EntityUtils.toString(response.getEntity()).contains(proxy.getHost());
                response.close();
                return isValid;
            } finally {
                response.close();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            httpclient.close();
        }
        return false;
    }

    private CredentialsProvider buildCredsProvider(Proxy proxy) {
        if (StringUtils.isBlank(proxy.getLogin())) {
            return null;
        }
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(proxy.getHost(), proxy.getPort()),
                new UsernamePasswordCredentials(proxy.getLogin(), proxy.getPassword()));
        return credsProvider;
    }

    private class MyConnectionSocketFactory extends SSLConnectionSocketFactory {
        MyConnectionSocketFactory(final SSLContext sslContext) {
            super(sslContext);
        }

        @Override
        public Socket createSocket(final HttpContext context) throws IOException {
            InetSocketAddress socksaddr = (InetSocketAddress) context.getAttribute("socks.address");
            java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS, socksaddr);
            return new Socket(proxy);
        }
    }

}
