package com.vk.promoengine.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Proxy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String host;
    private Integer port;
    private String login;
    private String password;
    private String scheme;

    public Proxy() {
    }

    public Proxy(String host, Integer port, String login, String password) {
        this.host = host;
        this.port = port;
        this.login = login;
        this.password = password;
    }

    public Proxy(String host, String port, String login, String password) {
        this.host = host;
        this.port = Integer.parseInt(port);
        this.login = login;
        this.password = password;
    }

    public Proxy(String host, Integer port) {
        this.host = host;
        this.port = port;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String schema) {
        this.scheme = schema;
    }

    @Override
    public String toString() {
        return "Proxy{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", login='" + login + '\'' +
                ", password='" + password + '\'' +
                ", scheme='" + scheme + '\'' +
                '}';
    }
}
