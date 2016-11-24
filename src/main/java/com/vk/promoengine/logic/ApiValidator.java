package com.vk.promoengine.logic;

import com.company.AnticaptchaApiWrapper;
import com.company.AnticaptchaResult;
import com.company.AnticaptchaTask;
import com.vk.promoengine.entities.Proxy;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xerces.impl.dv.util.Base64;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

public class ApiValidator extends AbstractWebHelper {

    private static final Logger log = Logger.getLogger(ApiValidator.class);
    private final static String SUCCESS_TOKEN = "success=1";
    private final String antigateApiHost = "api.anti-captcha.com";
    private final String antigateClientKey = "2e0442f05bdfb231d25c8289f1d3ad26";

    public ApiValidator(Proxy proxy) throws IOException {
        super(proxy);
    }

    public synchronized boolean validate(String validateUrl) {
        webDriver.get(validateUrl);
        try {
            WebElement skipLink = webDriver.findElement(By.cssSelector("#activation_wrap > div > form > div:nth-child(3) > a"));
            skipLink.click();
            WebElement captchaImage = webDriver.findElement(By.cssSelector("#captcha_wrap > div > div > form > div:nth-child(2) > img"));
            String base64 = imageToBase64(captchaImage);
            String captchaValue = solveCaptcha(base64);
            if (StringUtils.isNotBlank(captchaValue)) {
                WebElement captchaInput = webDriver.findElement(By.cssSelector("#captcha_wrap > div > div > form > div:nth-child(3) > div > input"));
                WebElement submitButton = webDriver.findElement(By.cssSelector("#captcha_wrap > div > div > form > div:nth-child(4) > input"));
                captchaInput.click();
                captchaInput.sendKeys(captchaValue);
                submitButton.click();
            } else {
                log.error("Failed to solve captcha");
            }
        } catch (NoSuchElementException e) {
            log.error(e.getMessage(), e);
            log.error(webDriver.getPageSource());
            takeScreenShot(webDriver);
        }
        String currentUrl = webDriver.getCurrentUrl();
        boolean success = currentUrl.contains(SUCCESS_TOKEN);
        if (!success) {
            log.error(String.format("URL %s not contains expected token '%s'", currentUrl, SUCCESS_TOKEN));
        }
        return success;
    }

    private String imageToBase64(WebElement captchaImage) {
        try {
            URL imageURL = new URL(captchaImage.getAttribute("src"));
            BufferedImage image = ImageIO.read(imageURL);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            byte[] bytes = out.toByteArray();
            return Base64.encode(bytes);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private String solveCaptcha(String base64) {
        if (StringUtils.isBlank(base64)) {
            log.error("Base64 image string is blank");
            return null;
        }
        base64 = base64.replace("\n", "").replace("\r", "");
        AnticaptchaTask task = null;
        while (task == null || StringUtils.equals("ERROR_NO_SLOT_AVAILABLE", task.getErrorCode())) {
            task = AnticaptchaApiWrapper.createImageToTextTask(antigateApiHost, antigateClientKey, base64);
        }
        if (StringUtils.isNotBlank(task.getErrorDescription())) {
            log.error(task.getErrorCode() + ": " + task.getErrorDescription());
            return null;
        }
        while (true) {
            AnticaptchaResult taskResult = AnticaptchaApiWrapper.getTaskResult(antigateApiHost, antigateClientKey, task);
            if (AnticaptchaResult.Status.ready.equals(taskResult.getStatus())) {
                return taskResult.getSolution();
            }
        }
    }

    public void close() {
        webDriver.quit();
    }
}
