package com.vk.promoengine.ui;

import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.data.Item;
import com.vaadin.server.ExternalResource;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.*;
import com.vk.promoengine.entities.Campaign;
import com.vk.promoengine.entities.PromotedUser;
import com.vk.promoengine.logic.PromoManager;

import java.util.Date;
import java.util.List;

@Push
//@PreserveOnRefresh
@Theme("tests-valo-reindeer")
public class Dashboard extends AbstractUI {

    private final PromoManager promoManager = PromoManager.getInstance();
    private final String ID = "id";
    private final String NAME = "name";
    private final String PROMOTED_COUNT = "promotedCount";
    private final String BUTTON = "button";
    private final String LAST_START = "lastStart";
    private final String LAST_STOP = "lastStop";
    private final String CHECK_BOX = "checkBox";
    private VerticalLayout rootLayout;
    private Table campaignsTable;

    @Override
    protected void init(VaadinRequest request) {
        rootLayout = new VerticalLayout();
        rootLayout.setSpacing(true);
        rootLayout.setMargin(true);
        rootLayout.setDefaultComponentAlignment(Alignment.TOP_CENTER);
        if (campaignsTable == null) {
            campaignsTable = buildCampaignsTable();
        }
        Button createButton = new Button("Create new campaign", FontAwesome.PLUS);
        createButton.addClickListener((Button.ClickListener) event -> setLocation("/create"));
        HorizontalLayout createButtonRow = new HorizontalLayout();
        createButtonRow.setDefaultComponentAlignment(Alignment.TOP_RIGHT);
        createButtonRow.setWidth("90%");
        createButtonRow.addComponent(createButton);
        rootLayout.addComponent(createButtonRow);
        rootLayout.addComponent(campaignsTable);
        Button deleteButton = createDeleteButton();
        HorizontalLayout deleteButtonRow = new HorizontalLayout();
        deleteButtonRow.setWidth("90%");
        deleteButtonRow.addComponents(deleteButton);
        deleteButtonRow.setComponentAlignment(deleteButton, Alignment.TOP_RIGHT);
        rootLayout.addComponents(deleteButtonRow);
        UI.getCurrent().getPage().setTitle("Promo Engine - Dashboard");
        setContent(rootLayout);
    }

    private Button createDeleteButton() {
        return new Button("Delete", FontAwesome.TRASH) {{
            addClickListener(new ClickListener() {
                public void buttonClick(ClickEvent event) {
                    event.getButton().setEnabled(false);
                    UI.getCurrent().push();
                    int deleted = 0;
                    for (Object itemId : campaignsTable.getItemIds()) {
                        if (((CheckBox) campaignsTable.getItem(itemId).getItemProperty(CHECK_BOX).getValue()).getValue()) {
                            promoManager.deleteCampaign((Long) itemId);
                            deleted++;
                        }
                    }
                    if (deleted > 0) {
                        setLocation("/");
                        Notification.show(deleted + " campaign(s) successfully deleted.", Notification.Type.TRAY_NOTIFICATION);
                    } else {
                        Notification.show("Select at least one campaign for delete.", Notification.Type.TRAY_NOTIFICATION);
                        UI.getCurrent().push();
                    }
                    event.getButton().setEnabled(true);
                }
            });
        }};
    }

    private Table buildCampaignsTable() {
        Table table = new Table("My campaigns");
        table.setWidth("90%");
        table.setImmediate(true);
        table.setPageLength(10);
        table.setSortEnabled(false);
        table.addContainerProperty(ID, Link.class, null);
        table.addContainerProperty(NAME, String.class, null);
        table.addContainerProperty(PROMOTED_COUNT, Integer.class, null);
        table.addContainerProperty(LAST_START, Date.class, null);
        table.addContainerProperty(LAST_STOP, Date.class, null);
        table.addContainerProperty(BUTTON, Button.class, null);
        table.addContainerProperty(CHECK_BOX, CheckBox.class, null);
        table.setColumnHeaders("ID", "Name", "Promoted", "Last start", "Last stop", "Start/Stop", "");
        for (Campaign campaign : promoManager.listCampaigns()) {
            final Long campaignId = campaign.getId();
            boolean isRunning = promoManager.isCampaignRunning(campaignId);
            Button startStopButton = new Button(isRunning ? FontAwesome.STOP.getHtml() : FontAwesome.PLAY.getHtml());
            startStopButton.setCaptionAsHtml(true);
            startStopButton.addClickListener((Button.ClickListener) event -> startStopCampaign(campaignId));
            CheckBox checkBox = new CheckBox();
            checkBox.setData(campaignId);
            table.addItem(
                    new Object[]{
                            new Link(
                                    campaignId.toString(),
                                    new ExternalResource(getContextPath() + "/details?id=" + campaignId)
                            ),
                            campaign.getName(),
                            getPromotedCount(campaign.getPromotedUsers()),
                            campaign.getLastStartTime(),
                            campaign.getLastStopTime(),
                            startStopButton,
                            checkBox
                    },
                    campaignId
            );
        }
        return table;
    }

    private int getPromotedCount(List<PromotedUser> promotedUsers) {
        int count = 0;
        /*for (PromotedUser promotedUser : promotedUsers) {
            if (promotedUser.isActionPerformed(PromoAction.Type.SEND_PROMO_MSG))
                count++;

        }*/
        return count;
    }

    private void startStopCampaign(Long campaignId) {
        final Item campaignRow = campaignsTable.getItem(campaignId);
        final Button button = (Button) campaignRow.getItemProperty(BUTTON).getValue();
        button.setEnabled(false);
        UI.getCurrent().push();
        boolean isRunning = promoManager.isCampaignRunning(campaignId);
        if (isRunning) {
            promoManager.stopCampaign(campaignId, promoter -> {
                button.setCaption(FontAwesome.PLAY.getHtml());
                button.setEnabled(true);
                UI.getCurrent().push();
                Notification.show(String.format("Campaign %s successfully stopped.", campaignId), Notification.Type.TRAY_NOTIFICATION);
            });
        } else {
            try {
                promoManager.startCampaign(campaignId);
                button.setCaption(FontAwesome.STOP.getHtml());
                Notification.show(String.format("Campaign %s successfully started.", campaignId), Notification.Type.TRAY_NOTIFICATION);
            } catch (Exception e) {
                Notification.show("Cannot start campaign: " + e.getMessage(), Notification.Type.ERROR_MESSAGE);
            }
            button.setEnabled(true);
            UI.getCurrent().push();
        }
    }
}
