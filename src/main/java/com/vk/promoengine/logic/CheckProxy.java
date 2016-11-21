package com.vk.promoengine.logic;


import com.vk.api.entitites.Proxy;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;

public class CheckProxy extends HttpServlet {

    Proxy proxy = null;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String url = "http://ip-api.com/json";
        URL obj = new URL(url);
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(System.getProperty("http.proxyUser"),
                        System.getProperty("http.proxyPassword").toCharArray());
            }
        });
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");
        int responseCode = con.getResponseCode();
        String responseMessage = con.getResponseMessage();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        PrintWriter printWriter = resp.getWriter();
        resp.setContentType("text/json");
        printWriter.print("Response Code: " + responseCode);
        printWriter.print("\nResponse Message: '" + responseMessage + "'");
        printWriter.print("\nResponse Body:\n" + response.toString());

        if (true) return;
        HttpGet request = new HttpGet(url);
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        if (proxy != null || resolveProxy()) {
            HttpHost proxyHost = new HttpHost(proxy.getHost(), proxy.getPort(), proxy.getSchema());
            RequestConfig config = RequestConfig
                    .custom()
                    .setProxy(proxyHost)
                    .build();
            request.setConfig(config);
            if (proxy.getLogin() != null) {
                credsProvider.setCredentials(
                        new AuthScope(proxy.getHost(), proxy.getPort()),
                        new UsernamePasswordCredentials(proxy.getLogin(), proxy.getPassword())
                );
            }
        }
        CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        CloseableHttpResponse httpResponse = httpClient.execute(request);
        HttpEntity entity = httpResponse.getEntity();
        String responseString = EntityUtils.toString(entity);
        PrintWriter writer = resp.getWriter();
        resp.setContentType("text/json");
        writer.print(responseString);
    }

    private boolean resolveProxy() {
        String isProxySet = System.getProperty("http.proxySet");
        if (StringUtils.isNotBlank(isProxySet) && Boolean.valueOf(isProxySet)) {
            proxy = new Proxy(
                    System.getProperty("http.proxyHost"),
                    Integer.parseInt(System.getProperty("http.proxyPort")),
                    System.getProperty("http.proxyUser"),
                    System.getProperty("http.proxyPassword"),
                    System.getProperty("http.proxySchema")
            );
        }
        return proxy != null;
    }
}
