package app.services;

import app.entities.User;

public interface PasswordResetEmailService {
    void sendReset(User recipient, String token);
}
