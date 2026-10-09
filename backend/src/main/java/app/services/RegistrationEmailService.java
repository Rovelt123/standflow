package app.services;

import app.entities.User;

public interface RegistrationEmailService {

    void sendWelcome(User recipient);
}
