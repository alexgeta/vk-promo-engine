package com.vk.promoengine.logic;

import com.gargoylesoftware.htmlunit.DefaultCredentialsProvider;
import com.gargoylesoftware.htmlunit.WebClient;
import com.vk.promoengine.entities.Proxy;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class AbstractWebHelper {

    private static final Logger log = Logger.getLogger(AbstractWebHelper.class);
    private static String PHANTOM_JS_EXE_PATH = null;
    protected final WebDriver webDriver;

    public AbstractWebHelper(Proxy proxy) throws IOException {
        loadProperties();
        webDriver = createHtmlUnitWebDriver(proxy);
//        webDriver = createPhantomJsWebDriver(proxy);
        configureWebDriver(webDriver);
    }

    protected void configureWebDriver(WebDriver webDriver) {
        webDriver.manage().window().setSize(new Dimension(1423, 770));
        webDriver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
    }

    private void loadProperties() throws IOException {
        if (PHANTOM_JS_EXE_PATH != null) {
            return;
        }
        Properties prop = new Properties();
        InputStream input = AbstractWebHelper.class.getResourceAsStream("/config.properties");
        prop.load(input);
        input.close();
        PHANTOM_JS_EXE_PATH = prop.getProperty("phantomjs.binary.path");
    }

    /*private WebDriver createPhantomJsWebDriver(Proxy proxy) {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setJavascriptEnabled(true);
        caps.setCapability("takesScreenshot", true);
        caps.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, PHANTOM_JS_EXE_PATH);
        caps.setCapability("phantomjs.page.settings.userAgent", getUserAgent());
        if(proxy != null) {
            caps.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, createProxyArgs(proxy));
        }
        return new PhantomJSDriver(caps);
    }*/

    protected String getUserAgent() {
        return "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";
    }

    private WebDriver createHtmlUnitWebDriver(final Proxy proxy) {
        DesiredCapabilities caps = createHtmlUnitCapabilities();
        HtmlUnitDriver htmlUnitDriver = new HtmlUnitDriver(caps) {
            @Override
            protected WebClient modifyWebClient(WebClient client) {
                DefaultCredentialsProvider credentialsProvider = new DefaultCredentialsProvider();
                credentialsProvider.addCredentials(proxy.getLogin(), proxy.getPassword());
                client.setCredentialsProvider(credentialsProvider);
                client.setJavaScriptTimeout(30000);
                client.getOptions().setThrowExceptionOnScriptError(false);
                return client;
            }
        };
        htmlUnitDriver.setProxy(proxy.getHost(), proxy.getPort());
        return htmlUnitDriver;
    }

    private DesiredCapabilities createHtmlUnitCapabilities() {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setJavascriptEnabled(true);
        caps.setBrowserName(BrowserType.CHROME);
//        caps.setPlatform(Platform.WIN10);
        caps.setPlatform(Platform.WIN8);
        return caps;
    }

    protected void takeScreenShot(WebDriver driver) {
        if (!(driver instanceof TakesScreenshot)) {
            return;
        }
        try {
            TakesScreenshot screenshot = (TakesScreenshot) driver;
            File scrFile = screenshot.getScreenshotAs(OutputType.FILE);
            String screenName = createScreenName() + ".png";
            FileUtils.copyFile(scrFile, new File("C:\\Users\\HOME\\Desktop" + File.separator + screenName));
//            log.debug(String.format("Screenshot '%s' successfully saved", screenName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String createScreenName() {
        return String.format("%s-%s-%d", getClass().getSimpleName(), Thread.currentThread().getName(), System.currentTimeMillis());
    }

    protected List<String> createProxyArgs(Proxy proxy) {
        List<String> result = new ArrayList<>();
        result.add(String.format("--proxy=%s:%d", proxy.getHost(), proxy.getPort()));
        result.add("--proxy-type=" + proxy.getScheme());
        if (proxy.getLogin() != null) {
            result.add(String.format("--proxy-auth=%s:%s", proxy.getLogin(), proxy.getPassword()));
        }
        return result;
    }

}
