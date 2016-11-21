package com.vk.promoengine.ui;


import com.vaadin.annotations.Theme;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.*;
import com.vk.promoengine.entities.Campaign;
import com.vk.promoengine.entities.PromoInfo;
import com.vk.promoengine.entities.VkAccount;
import com.vk.promoengine.logic.PromoManager;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

@Theme("tests-valo-reindeer")
public class Edit extends AbstractUI {

    private VerticalLayout rootLayout = new VerticalLayout();
    private PromoManager promoManager = PromoManager.getInstance();
    private TextArea accounts = new TextArea("Accounts");
    private TextArea welcomeMessages = new TextArea("Welcome messages");
    private TextArea promoMessages = new TextArea("Promo messages");

    @Override
    protected void init(VaadinRequest request) {
        rootLayout.setSpacing(true);
        rootLayout.setMargin(true);
        String id = request.getParameter("id");
        if (id != null) {
            buildEditFields(Long.parseLong(id));
        } else {
            Label label = new Label("There are no campaign ID for displaying.");
            label.setStyleName("h4");
            rootLayout.addComponent(label);
        }
        rootLayout.addComponent(homeButton);
        rootLayout.setComponentAlignment(homeButton, Alignment.BOTTOM_LEFT);
        setContent(rootLayout);

    }

    private void buildEditFields(final long id) {
        Campaign campaign = promoManager.getCampaign(id);
        PromoInfo promoInfo = campaign.getPromoInfo();
        accounts.setValue(accountsToString(campaign.getAccounts()));
        accounts.setSizeFull();
        welcomeMessages.setValue(StringUtils.join(promoInfo.getWelcomeMessages(), "\n"));
        welcomeMessages.setSizeFull();
        promoMessages.setValue(StringUtils.join(promoInfo.getPromoMessages(), "\n"));
        promoMessages.setSizeFull();
        Button saveButton = new Button("Save", FontAwesome.SAVE);
        saveButton.addClickListener((Button.ClickListener) event -> {
            promoManager.handleCampaign(id, campaign1 -> {
                PromoInfo campaignPromoInfo = campaign1.getPromoInfo();
                Map<String, String> usernamePasswordInput = new HashMap<>();
                splitByNewLine(accounts.getValue()).forEach(s -> {
                    String[] userPassEntry = s.split(":");
                    usernamePasswordInput.put(userPassEntry[0], userPassEntry[1]);
                });
                List<VkAccount> accounts = campaign1.getAccounts();
                processDelete(usernamePasswordInput, accounts);
                processAdd(usernamePasswordInput, accounts);
                campaignPromoInfo.setWelcomeMessages(splitByNewLine(welcomeMessages.getValue()));
                campaignPromoInfo.setPromoMessages(splitByNewLine(promoMessages.getValue()));
                return null;
            });
            setLocation("/details?id=" + String.valueOf(id));
        });
        rootLayout.addComponents(welcomeMessages, promoMessages, accounts, saveButton);
    }

    private void processAdd(Map<String, String> inputAccs, List<VkAccount> currentAccs) {
        Set<String> existingAccs = new HashSet<>();
        currentAccs.forEach(vkAccount -> existingAccs.add(vkAccount.getUsername()));
        for (Map.Entry<String, String> usernamePasswordEntry : inputAccs.entrySet()) {
            String userName = usernamePasswordEntry.getKey();
            String password = usernamePasswordEntry.getValue();
            if (!existingAccs.contains(userName)) {
                currentAccs.add(new VkAccount(userName, password));
            }
        }


    }

    private void processDelete(Map<String, String> inputAccs, List<VkAccount> currentAccs) {
        Iterator<VkAccount> accountIterator = currentAccs.iterator();
        while (accountIterator.hasNext()) {
            VkAccount vkAccount = accountIterator.next();
            if (!inputAccs.containsKey(vkAccount.getUsername())) {
                accountIterator.remove();
            }
        }
    }

    private String accountsToString(List<VkAccount> accounts) {
        List<String> accsLines = new ArrayList<>();
        accounts.forEach(vkAccount -> accsLines.add(vkAccount.getUsername() + ":" + vkAccount.getPassword()));
        return StringUtils.join(accsLines, "\n");
    }

    private List<String> splitByNewLine(String string) {
        HashSet<String> result = new HashSet<>();
        if (StringUtils.isNotBlank(string)) {
            List<String> lines = Arrays.asList(string.trim().split("[\\r\\n]+"));
            for (String line : lines) {
                if (StringUtils.isNotBlank(line)) {
                    result.add(line.trim());
                }
            }
        }
        return new ArrayList<>(result);
    }
}
