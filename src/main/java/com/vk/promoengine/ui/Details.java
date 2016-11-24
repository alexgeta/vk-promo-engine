package com.vk.promoengine.ui;

import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.data.Property;
import com.vaadin.server.ExternalResource;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.VaadinRequest;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import com.vk.promoengine.entities.*;
import com.vk.promoengine.logic.CampaignHandler;
import com.vk.promoengine.logic.PromoManager;
import com.vk.promoengine.logic.UploadHelper;
import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Push
@Theme("tests-valo-reindeer")
public class Details extends AbstractUI {

    private PromoManager promoManager = PromoManager.getInstance();
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private Map<String, Label> actionCountMap = new HashMap<>();

    @Override
    protected void init(VaadinRequest request) {
        String id = request.getParameter("id");
        try {
            promoManager.handleCampaign(Long.parseLong(id), new CampaignHandler<Campaign>() {
                @Override
                public Campaign handle(Campaign campaign) {
                    setContent(buildContent(campaign));
                    return null;
                }
            });
        } catch (Exception e) {
            setLocation("/");
        }
    }

    private Component buildContent(Campaign campaign) {
        VerticalLayout rootLayout = new VerticalLayout();
        rootLayout.setSpacing(true);
        rootLayout.setMargin(true);
        rootLayout.setDefaultComponentAlignment(Alignment.TOP_CENTER);
        Table welcomeMsgs = buildAndFillTable(null, "Message", campaign.getPromoInfo().getWelcomeMessages());
        Table promoMsgs = buildAndFillTable(null, "Message", campaign.getPromoInfo().getPromoMessages());
        Table accounts = buildAccountsTable(campaign);
        Table proxies = buildProxiesTable(campaign.getProxies());
        HorizontalLayout body = new HorizontalLayout();
        body.setSizeFull();
        body.setSpacing(true);
        body.setMargin(false);
        FormLayout messagesTables = new FormLayout();
        messagesTables.setMargin(true);
        messagesTables.addComponents(
                new Label(wrapBold("Welcome messages:"), ContentMode.HTML),
                welcomeMsgs,
                new Label(wrapBold("Promo messages:"), ContentMode.HTML),
                promoMsgs,
                new Label(wrapBold("Accounts:"), ContentMode.HTML),
                accounts,
                new Label(wrapBold("Proxies:"), ContentMode.HTML),
                proxies,
                new Label(wrapBold("Promoted users limit: ") + campaign.getPromoInfo().getPromotedUsersLimit(), ContentMode.HTML),
                buildTargetUsersRow(campaign));
        String groupId = campaign.getPromoInfo().getGroupId();
        if (StringUtils.isNotBlank(groupId)) {
            String groupUrl = "http://vk.com/public" + groupId;
            messagesTables.addComponent(
                    new Label(wrapBold("Promoting public page: ") + "<a href=\"" + groupUrl + "\" target=\"_blank\">" + groupUrl + "</a>",
                            ContentMode.HTML)
            );
        }
        List<PromotedUser> promotedUsers = campaign.getPromotedUsers();
        Table actionStatTable = buildActionStatTable();
        ComboBox days = buildDaysComboBox(promotedUsers, actionStatTable);
        FormLayout actionsLayout = new FormLayout(new Label("Action details"), days, actionStatTable);
        actionsLayout.setMargin(false);
        addActionsTotals(actionsLayout);
        populateStatTable(promotedUsers, actionStatTable, null);
        body.addComponents(messagesTables, actionsLayout);
        UI.getCurrent().getPage().setTitle(campaign.getName());
        rootLayout.addComponent(body);
        rootLayout.setComponentAlignment(body, Alignment.TOP_CENTER);
        rootLayout.setSpacing(true);
        rootLayout.setMargin(true);
        rootLayout.setDefaultComponentAlignment(Alignment.TOP_CENTER);
        Button editButton = new Button("Edit", FontAwesome.EDIT);
        editButton.addClickListener((Button.ClickListener) event -> setLocation("/edit?id=" + campaign.getId()));
        HorizontalLayout backEditRow = new HorizontalLayout();
        backEditRow.setSpacing(true);
        backEditRow.addComponents(homeButton, editButton);
        rootLayout.addComponents(backEditRow);
        rootLayout.setComponentAlignment(backEditRow, Alignment.BOTTOM_LEFT);
        return rootLayout;
    }

    private Table buildProxiesTable(List<Proxy> proxies) {
        Table result = new Table();
        result.setWidth("99%");
        result.setImmediate(true);
        result.setSortEnabled(false);
        result.addContainerProperty("id", Integer.class, null);
        result.addContainerProperty("host", String.class, null);
        result.addContainerProperty("port", String.class, null);
        result.addContainerProperty("userName", String.class, null);
        result.addContainerProperty("passWord", String.class, null);
        result.setColumnHeaders("#", "Host", "Port", "Username", "Password");
        int index = 0;
        for (Proxy proxy : proxies) {
            result.addItem(
                    new Object[]{
                            ++index,
                            proxy.getHost(),
                            proxy.getPort().toString(),
                            StringUtils.stripToEmpty(proxy.getLogin()),
                            StringUtils.stripToEmpty(proxy.getPassword())
                    },
                    index
            );
        }
        result.setPageLength(index);
        return result;
    }

    private Table buildAccountsTable(Campaign campaign) {
        Table result = new Table();
        result.setWidth("99%");
        result.setImmediate(true);
        result.setSortEnabled(false);
        result.addContainerProperty("id", Integer.class, null);
        result.addContainerProperty("fullName", Link.class, null);
        result.addContainerProperty("userName", String.class, null);
        result.addContainerProperty("passWord", String.class, null);
        result.addContainerProperty("welcomeMsgSent", Integer.class, null);
        result.addContainerProperty("promoMsgSent", Integer.class, null);
        result.addContainerProperty("status", String.class, null);
        result.setColumnHeaders("#", "Full Name", "Username", "Password",
                FontAwesome.COMMENT_O.getHtml(), FontAwesome.COMMENTING_O.getHtml(), "Status");
        Map<String, Integer> accountWelcomeMsgSent = countAction(campaign.getPromotedUsers(), PromoAction.Type.SEND_WELCOME_MSG);
        Map<String, Integer> accountPromoMsgSent = countAction(campaign.getPromotedUsers(), PromoAction.Type.SEND_PROMO_MSG);
        int index = 0;
        for (VkAccount account : campaign.getAccounts()) {
            result.addItem(
                    new Object[]{
                            ++index,
                            createAccountLink(account),
                            account.getUsername(),
                            account.getPassword(),
                            accountWelcomeMsgSent.get(account.getVkId()),
                            accountPromoMsgSent.get(account.getVkId()),
                            account.getStatus().name()
                    },
                    index
            );
        }
        result.setPageLength(10);
        return result;
    }

    private Map<String, Integer> countAction(List<PromotedUser> promotedUsers, PromoAction.Type actionType) {
        Map<String, Integer> result = new HashMap<>();
        for (PromotedUser promotedUser : promotedUsers) {
            if (!promotedUser.isActionPerformed(actionType)) {
                continue;
            }
            String promoterId = promotedUser.getPromoterId();
            Integer counter = result.get(promoterId);
            if (counter == null) {
                counter = 0;
            }
            result.put(promoterId, counter + 1);
        }
        return result;
    }

    private Link createAccountLink(VkAccount account) {
        Link link = new Link();
        link.setTargetName("_blank");
        if (StringUtils.isBlank(account.getFullName())) {
            link.setCaption("N/A");
            link.setEnabled(false);
        } else {
            link.setCaption(account.getFullName());
            link.setResource(new ExternalResource("http://vk.com/id" + account.getVkId()));
        }
        return link;
    }

    private ComboBox buildDaysComboBox(final List<PromotedUser> promotedUsers, final Table actionStatTable) {
        ComboBox result = new ComboBox();
        result.setTextInputAllowed(false);
        result.setNullSelectionAllowed(false);
        final String ALL = "All";
        Set<String> days = new LinkedHashSet<>();
        for (PromotedUser promotedUser : promotedUsers) {
            for (PromoAction promoAction : promotedUser.getPerformedActions()) {
                days.add(sdf.format(promoAction.getActionTime()));
            }
        }
        TreeSet<Date> sortedDays = new TreeSet<>((o1, o2) -> (int) (o2.getTime() - o1.getTime()));
        for (String day : days) {
            try {
                sortedDays.add(sdf.parse(day));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        days.clear();
        for (Date day : sortedDays) {
            days.add(sdf.format(day));
        }
        result.addItem(ALL);
        result.select(ALL);
        result.addItems(days);
        result.addValueChangeListener((Property.ValueChangeListener) event -> {
            String selected = event.getProperty().getValue().toString();
            populateStatTable(promotedUsers, actionStatTable, selected.equals(ALL) ? null : selected);
        });
        return result;
    }

    private HorizontalLayout buildTargetUsersRow(final Campaign campaign) {
        final HorizontalLayout layout = new HorizontalLayout();
        layout.setDefaultComponentAlignment(Alignment.MIDDLE_LEFT);
        final Upload idsUploader = UploadHelper.createIdsUploader();
        idsUploader.addFinishedListener((Upload.FinishedListener) event -> {
            int added = updateTargetUsers(UploadHelper.getLines(idsUploader), campaign.getId());
            Notification.show(added + " ids added to target users set.");
            UploadHelper.clearUploader(idsUploader);
        });
        idsUploader.setButtonCaption("Update");
        layout.setSpacing(true);
        layout.addComponent(new Label(wrapBold("Available target users: ") + campaign.getTargetUsers().size(), ContentMode.HTML));
        if (!promoManager.isCampaignRunning(campaign.getId())) {
            layout.addComponent(new Button("Add users", (Button.ClickListener) event -> {
                layout.removeComponent(event.getButton());
                layout.addComponent(idsUploader);
            }));
        }
        return layout;
    }

    private int getTargetUsersCount(long campaignId) {
        return promoManager.handleCampaign(campaignId, campaign -> campaign.getTargetUsers().size());
    }

    private int updateTargetUsers(final List<String> newTargetUsers, long campaignId) {
        if (promoManager.isCampaignRunning(campaignId)) {
            return 0;
        }
        return promoManager.handleCampaign(campaignId, campaign -> {
            List<String> targetUsers = campaign.getTargetUsers();
            Set<String> restricted = new HashSet<>(targetUsers);
            for (PromotedUser promotedUser : campaign.getPromotedUsers()) {
                restricted.add(promotedUser.getVkId());
            }
            int result = 0;
            for (String user : newTargetUsers) {
                if (!restricted.contains(user)) {
                    targetUsers.add(user);
                    result++;
                }
            }
            return result;
        });
    }

    private void addActionsTotals(/*List<PromotedUser> userList, */AbstractComponentContainer container) {
        for (PromoAction.Type actionType : PromoAction.Type.values()) {
            /*int result = 0;
            for (PromotedUser user : userList) {
                for (PromoAction promoAction : user.getPerformedActions()) {
                    if(promoAction.getActionType().equals(actionType)){
                        result++;
                    }
                }
            }*/
            HorizontalLayout line = new HorizontalLayout();
            line.setSpacing(true);
            line.addComponent(new Label(wrapBold("Total " + actionType + " actions: "), ContentMode.HTML));
            Label count = new Label("0", ContentMode.HTML);
            line.addComponent(count);
            container.addComponent(line);
            actionCountMap.put(actionType.name(), count);
        }
    }

    private String wrapBold(String str) {
        return "<b>" + str + "</b>";
    }

    private Table buildActionStatTable() {
        Table table = new Table();
        table.addContainerProperty("user_id", Link.class, null);
        table.setColumnExpandRatio("user_id", 15);
        table.addContainerProperty("actions", String.class, null);
        table.setColumnExpandRatio("actions", 10);
        table.addContainerProperty("promoter_id", String.class, null);
        table.setColumnExpandRatio("promoter_id", 15);
        table.addContainerProperty("lastActionTime", String.class, null);
        table.setColumnExpandRatio("lastActionTime", 30);
        table.addContainerProperty("message_log", PopupView.class, null);
        table.setColumnExpandRatio("message_log", 15);
        table.setColumnHeaders("User", "Actions", "Promoter", "Time", "Message Log");
        table.setPageLength(23);
        table.setWidth("99%");
        return table;
    }

    private void populateStatTable(List<PromotedUser> promotedUsers, Table actionStats, String day) {
        int counter = 0;
        actionStats.removeAllItems();
        resetActionCounters();
        for (PromotedUser promotedUser : promotedUsers) {
            PromoAction lastAction = getLastTimePromoAction(promotedUser.getPerformedActions());
            if (day == null || day.equals(sdf.format(lastAction.getActionTime()))) {
                String vkId = promotedUser.getVkId();
                Link link = new Link(vkId, new ExternalResource("http://vk.com/id" + vkId));
                link.setTargetName("_blank");
                actionStats.addItem(
                        new Object[]{
                                link,
                                actionsToString(promotedUser.getPerformedActions()),
                                promotedUser.getPromoterId(),
                                lastAction.getActionTime().toString(),
                                createMessageLogPopup(promotedUser)
                        },
                        counter++
                );
                for (PromoAction promoAction : promotedUser.getPerformedActions()) {
                    Label count = actionCountMap.get(promoAction.getActionType().name());
                    count.setValue(Integer.parseInt(count.getValue()) + 1 + "");
                }
            }
        }
        hideZeroCounters();
        actionStats.sort(new Object[]{"lastActionTime"}, new boolean[]{false});
    }

    private void resetActionCounters() {
        for (Map.Entry<String, Label> entry : actionCountMap.entrySet()) {
            entry.getValue().setValue("0");
            entry.getValue().getParent().setVisible(true);
        }
    }

    private void hideZeroCounters() {
        actionCountMap.entrySet().stream().filter(entry -> StringUtils.equals(entry.getValue().getValue(), "0")).forEach(entry -> {
            entry.getValue().getParent().setVisible(false);
        });
    }

    private PopupView createMessageLogPopup(PromotedUser promotedUser) {
        return new PopupView(new PopupView.Content() {
            @Override
            public String getMinimizedValueAsHTML() {
                return FontAwesome.COMMENTS_O.getHtml();
            }

            @Override
            public Component getPopupComponent() {
                return new HorizontalLayout(new Label(promotedUser.getMessageLog(), ContentMode.PREFORMATTED));
            }
        });
    }

    private String actionsToString(Set<PromoAction> promoActions) {
        List<String> actionsString = new ArrayList<>();
        Set<PromoAction.Type> actionTypes = new HashSet<>();
        for (PromoAction promoAction : promoActions) {
            actionTypes.add(promoAction.getActionType());
        }
        if (actionTypes.contains(PromoAction.Type.SEND_WELCOME_MSG)) {
            actionsString.add("W");
        }
        if (actionTypes.contains(PromoAction.Type.SEND_PROMO_MSG)) {
            actionsString.add("P");
        }
        return StringUtils.join(actionsString, ", ");
    }

    private PromoAction getLastTimePromoAction(Set<PromoAction> promoActions) {
        PromoAction lastAction = null;
        long lastTime = 0;
        for (PromoAction promoAction : promoActions) {
            if (promoAction.getActionTime().getTime() > lastTime) {
                lastTime = promoAction.getActionTime().getTime();
                lastAction = promoAction;
            }
        }
        return lastAction;
    }

    private Table buildAndFillTable(String tableCaption, String columnCaption, Collection<String> items) {
        Table result = new Table(tableCaption);
        result.setWidth("99%");
        result.setImmediate(true);
        result.setSortEnabled(false);
        result.addContainerProperty("id", Integer.class, null);
        result.addContainerProperty("msg", String.class, null);
        result.setColumnExpandRatio("id", 1);
        result.setColumnExpandRatio("msg", 9);
        result.setColumnHeaders("#", columnCaption);
        int index = 0;
        for (String item : items) {
            result.addItem(new Object[]{++index, item}, index);
        }
        result.setPageLength(index);
        return result;
    }
}
