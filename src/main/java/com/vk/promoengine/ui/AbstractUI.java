package com.vk.promoengine.ui;


import com.vaadin.server.FontAwesome;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.Button;
import com.vaadin.ui.UI;

public abstract class AbstractUI extends UI {

    protected Button homeButton = new Button("Home", FontAwesome.HOME);

    public AbstractUI() {
        homeButton.addClickListener((Button.ClickListener) event -> setLocation("/"));
    }

    protected void setLocation(String location) {
        UI.getCurrent()
                .getPage()
                .setLocation(getContextPath() + location);
    }

    protected String getContextPath() {
        return VaadinServlet.getCurrent().getServletContext().getContextPath();
    }
}
