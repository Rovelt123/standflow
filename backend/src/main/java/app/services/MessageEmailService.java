package app.services;

import app.entities.Message;
import app.entities.User;

public interface MessageEmailService {

    void sendAdminMessageNotification(User recipient, Message message);

    //--------------------------------------------------------------

    default void sendCustomerMessageNotification(User recipient, Message message) {
        sendAdminMessageNotification(recipient, message);
    }
}
