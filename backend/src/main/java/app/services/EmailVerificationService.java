package app.services;

import app.entities.User;

public interface EmailVerificationService {
    void sendVerification(User recipient, String token);
}
