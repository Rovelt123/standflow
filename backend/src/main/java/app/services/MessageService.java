package app.services;

import app.enums.Notifications;

public class MessageService {

    public String buildMessage(Notifications notifications, String... args) {
        return String.format(notifications.getDisplayName(), args);
    }

}
