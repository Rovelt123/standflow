package app.services;

import app.entities.Application;

public interface ApplicationEmailService {

    void sendApplication(Application application, byte[] pdfAttachment, String attachmentFilename);
}
