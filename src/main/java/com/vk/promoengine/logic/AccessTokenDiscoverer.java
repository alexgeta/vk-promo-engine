package com.vk.promoengine.logic;

import com.google.common.base.Splitter;
import com.vk.promoengine.entities.Proxy;
import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;

public class AccessTokenDiscoverer extends AbstractWebHelper {

    private static final Logger log = Logger.getLogger(AccessTokenDiscoverer.class);
    private static final String AUTH_URL = "https://oauth.vk.com/authorize?client_id=2685278&v=5.40&scope=messages,friends,groups,offline&redirect_uri=http://oauth.vk.com/blank.html&display=page&response_type=token";

    public AccessTokenDiscoverer(Proxy proxy) throws IOException {
        super(proxy);
    }

    public String retrieveToken(String login, String pass) {
        String result = null;
        log.debug("Retrieving token for '" + login + ":" + pass + "' ...");
        webDriver.get(AUTH_URL);
        WebElement emailInput = webDriver.findElement(By.name("email"));
        WebElement passwordInput = webDriver.findElement(By.name("pass"));
        WebElement loginButton = webDriver.findElement(By.id("install_allow"));
        setInputValue(emailInput, login);
        setInputValue(passwordInput, pass);
        loginButton.click();
        if (hasValidateCodeElement(webDriver)) {
            submitValidateCode(login);
        }
        try {
            loginButton = webDriver.findElement(By.cssSelector("button.flat_button:nth-child(1)"));
            loginButton.click();
        } catch (Exception ignored) {
        }
        String url = webDriver.getCurrentUrl();
        try {
            result = Splitter.on('&').trimResults().withKeyValueSeparator("=").split(url.split("#")[1]).get("access_token");
            log.debug(String.format("Access token retrieved '%s'", result));
        } catch (Exception e) {
            log.error("Failed to extract access token from URL: " + url);
        }
        webDriver.quit();
        return result;
    }

    private void submitValidateCode(String login) {
        WebElement code = webDriver.findElement(By.name("code"));
        WebElement validateBtn = webDriver.findElement(By.className("button"));
        code.click();
        code.sendKeys(login.substring(1, login.length() - 2));
        validateBtn.click();
    }

    private boolean hasValidateCodeElement(WebDriver webDriver) {
        WebElement code;
        try {
            code = webDriver.findElement(By.name("code"));
        } catch (Exception e) {
            return false;
        }
        return code != null;
    }

    private void setInputValue(WebElement input, String value) {
        input.click();
        input.sendKeys(value);
    }
}
