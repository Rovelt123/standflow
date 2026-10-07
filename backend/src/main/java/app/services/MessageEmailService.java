package app.services;

import app.entities.Message;
import app.entities.User;

public interface MessageEmailService {

    void sendAdminMessageNotification(User recipient, Message message);
}
