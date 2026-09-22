package app.services;

import app.daos.TemplateDAO;
import app.server.Setup;

public class TemplateService {

    private final TemplateDAO templateDAO;

    // ________________________________________________________

    public TemplateService() {
        this(new TemplateDAO(Setup.em));
    }

    // ________________________________________________________

    public TemplateService(TemplateDAO templateDAO) {
        this.templateDAO = templateDAO;
    }

    // ________________________________________________________

}
