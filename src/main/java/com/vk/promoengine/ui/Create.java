package com.vk.promoengine.ui;


import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.data.Validatable;
import com.vaadin.data.Validator;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.*;
import com.vk.promoengine.entities.Campaign;
import com.vk.promoengine.entities.PromoInfo;
import com.vk.promoengine.entities.Proxy;
import com.vk.promoengine.entities.VkAccount;
import com.vk.promoengine.logic.PromoManager;
import com.vk.promoengine.logic.UploadHelper;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

@Push
@Theme("tests-valo-reindeer")
public class Create extends AbstractUI {

    private PromoManager promoManager = PromoManager.getInstance();
    private VerticalLayout rootLayout;
    private int minMessageCount = 3;
    private TextField campaignName = new TextField("Campaign name");
    private TextField groupLink = new TextField("VK group link");
    private TextField targetUsersCount = new TextField("Promoted users limit");
    private TextArea accounts = new TextArea("Accounts");
    private TextArea proxies = new TextArea("Proxies");
    private TextArea welcomeMessages = new TextArea("Welcome messages");
    private TextArea promoMessages = new TextArea("Promo messages");
    private Set<Validatable> validatables = new LinkedHashSet<>();
    private Upload userIdsUploader = UploadHelper.createIdsUploader();

    @Override
    protected void init(VaadinRequest request) {
        rootLayout = new VerticalLayout();
        rootLayout.setSpacing(true);
        rootLayout.setMargin(true);
        welcomeMessages.setInputPrompt("Welcome messages goes here separated by newline");
        promoMessages.setInputPrompt("Promo messages goes here separated by newline");
        accounts.setInputPrompt("userName:p@55w0rD");
        proxies.setInputPrompt("127.0.0.1:8080:username:password");
        setRequired(true, /*campaignName, targetUsersCount,*/ welcomeMessages, promoMessages, accounts, proxies);
        setSize("95%", null, welcomeMessages, promoMessages, accounts, proxies);
        HorizontalLayout firstRow = new HorizontalLayout(campaignName, /*targetUsersCount, groupLink, */userIdsUploader);
        HorizontalLayout secondRow = new HorizontalLayout(welcomeMessages, promoMessages, accounts, proxies);
        secondRow.setWidth("100%");
        firstRow.setSpacing(true);
        secondRow.setSpacing(true);
        addMessagesCountValidators(welcomeMessages, promoMessages);
        targetUsersCount.setConverter(Integer.class);
        targetUsersCount.setNullRepresentation(null);
        rootLayout.addComponent(firstRow);
        rootLayout.addComponent(secondRow);
        Button submitButton = createSubmitButton();
        HorizontalLayout submitRow = new HorizontalLayout();
        submitRow.setSpacing(true);
        submitRow.setWidth("98%");
        submitRow.addComponents(homeButton, submitButton);
        submitRow.setComponentAlignment(submitButton, Alignment.TOP_RIGHT);
        rootLayout.addComponent(submitRow);
        setContent(rootLayout);
    }

    private void addMessagesCountValidators(AbstractField... fields) {
        for (AbstractField field : fields) {
            final String caption = field.getCaption();
            field.addValidator((Validator) value -> {
                if (value == null || StringUtils.isBlank(value.toString())) {
                    throw new Validator.EmptyValueException("Field '" + caption + "' can't be empty");
                }
                if (splitByNewLine(value.toString()).size() < minMessageCount) {
                    throw new Validator.InvalidValueException(
                            "Field '" + caption + "' must contain at least " + minMessageCount + " unique rows.");
                }
            });
        }
    }

    private Button createSubmitButton() {
        Button result = new Button("Create campaign");
        result.setImmediate(true);
        result.addClickListener((Button.ClickListener) event -> {
            try {
                if (!validateInputs()) {
                    return;
                }
                event.getButton().setEnabled(false);
                UI.getCurrent().push();
                PromoInfo promoInfo = new PromoInfo(
                        new ArrayList<>(splitByNewLine(welcomeMessages.getValue())),
                        new ArrayList<>(splitByNewLine(promoMessages.getValue())),
                        groupLink.getValue(),
                        getPromotedUsersLimit()
                );
                promoManager.createCampaign(new Campaign(
                        campaignName.getValue(),
                        promoInfo,
                        UploadHelper.getLines(userIdsUploader),
                        parseAccounts(accounts),
                        parseProxies(proxies))
                );
                event.getButton().setEnabled(true);
                setLocation("/");
            } catch (Exception e) {

                e.printStackTrace();
                Notification.show(e.getMessage(), Notification.Type.ERROR_MESSAGE);
                event.getButton().setEnabled(true);
            }
        });
        return result;
    }

    private int getPromotedUsersLimit() {
        try {
            return Integer.parseInt(targetUsersCount.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<VkAccount> parseAccounts(TextArea accounts) {
        List<VkAccount> result = new ArrayList<>();
        Set<String> accsLines = splitByNewLine(accounts.getValue());
        for (String accLine : accsLines) {
            String[] accEntry = accLine.trim().split(":");
            result.add(new VkAccount(accEntry[0], accEntry[1]));
        }
        return result;
    }

    private List<Proxy> parseProxies(TextArea proxies) {
        List<Proxy> result = new ArrayList<>();
        Set<String> proxyLines = splitByNewLine(proxies.getValue());
        for (String proxyLine : proxyLines) {
            String[] proxyEntry = proxyLine.trim().split(":");
            result.add(new Proxy(
                    getByIndex(proxyEntry, 0),
                    getByIndex(proxyEntry, 1),
                    getByIndex(proxyEntry, 2),
                    getByIndex(proxyEntry, 3))
            );
        }
        return result;
    }

    private String getByIndex(String[] array, int index) {
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    private boolean validateInputs() {
        try {
            if (UploadHelper.getLines(userIdsUploader).isEmpty()) {
                throw new Validator.InvalidValueException("Target users IDs list is required!");
            }
            for (Validatable validatable : validatables) {
                validatable.validate();
            }
        } catch (Validator.InvalidValueException e) {
            Notification.show(e.getMessage(), Notification.Type.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private Set<String> splitByNewLine(String string) {
        Set<String> result = new HashSet<>();
        if (StringUtils.isNotBlank(string)) {
            List<String> lines = Arrays.asList(string.trim().split("[\\r\\n]+"));
            for (String line : lines) {
                if (StringUtils.isNotBlank(line)) {
                    result.add(line.trim());
                }
            }
        }
        return result;
    }


    private void setRequired(boolean isRequired, AbstractTextField... fields) {
        for (AbstractTextField field : fields) {
            field.setRequired(isRequired);
            if (isRequired) {
                field.setRequiredError("Field '" + field.getCaption() + "' can't be empty.");
                validatables.add(field);
            } else {
                validatables.remove(field);
            }
        }
    }

    private void setSize(String width, String height, AbstractTextField... fields) {
        for (AbstractTextField field : fields) {
            if (StringUtils.isNotBlank(width)) {
                field.setWidth(width);
            }
            if (StringUtils.isNotBlank(height)) {
                field.setHeight(height);
            }
        }
    }

}
