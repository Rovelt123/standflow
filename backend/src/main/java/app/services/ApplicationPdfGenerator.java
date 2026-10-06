package app.services;

import app.entities.Application;

public interface ApplicationPdfGenerator {

    byte[] generate(Application application);

    //--------------------------------------------------------------

    default String filename(Application application) {
        return "application-" + application.getId() + ".pdf";
    }
}
