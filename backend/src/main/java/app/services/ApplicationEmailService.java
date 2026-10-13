package app.services;

import app.entities.Application;

public interface ApplicationEmailService {

    void sendApplication(Application application, byte[] pdfAttachment, String attachmentFilename);

    //--------------------------------------------------------------

    default void sendStandTypeChange(Application application, String previousStandType) {
        throw new UnsupportedOperationException("Standtype-mail er ikke konfigureret.");
    }
}
