package app.services;

import app.entities.User;

public interface NewsletterEmailService {

    void sendNewsletter(User recipient, String subject, String body);
}
