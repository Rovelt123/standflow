package app.services;

import app.daos.TemplateDAO;
import app.dtos.TemplateDTO;
import app.entities.Template;
import app.exceptions.ApiException;
import app.mappers.TemplateMapper;
import app.server.Setup;
import app.utils.ErrorHandler;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TemplateService {

    private final TemplateDAO templateDAO;
    private final TemplateMapper mapper = new TemplateMapper();

    //--------------------------------------------------------------

    public TemplateService() {
        this(new TemplateDAO(Setup.em));
    }

    //--------------------------------------------------------------

    public TemplateService(TemplateDAO templateDAO) {
        this.templateDAO = Objects.requireNonNull(templateDAO);
    }

    //--------------------------------------------------------------

    public List<Template> getAll() {
        return templateDAO.getAll();
    }

    //--------------------------------------------------------------

    public Template getById(UUID id) {
        return ErrorHandler.tryEntity(templateDAO.getById(id), "Skabelonen blev ikke fundet.");
    }

    //--------------------------------------------------------------

    public TemplateDTO create(TemplateDTO request) {
        Template template = mapper.toEntity(validate(request, null));
        try {
            return mapper.toDTO(templateDAO.create(template));
        } catch (RuntimeException exception) {
            throw new ApiException(500, "Skabelonen kunne ikke gemmes.");
        }
    }

    //--------------------------------------------------------------

    public TemplateDTO update(UUID id, TemplateDTO request) {
        TemplateDTO normalized = validate(request, id);
        getById(id);
        try {
            return mapper.toDTO(templateDAO.update(mapper.toEntity(normalized)));
        } catch (RuntimeException exception) {
            throw new ApiException(500, "Skabelonen kunne ikke gemmes.");
        }
    }

    //--------------------------------------------------------------

    /** The id is server/path controlled; placeholders such as <navn> in the body are stored raw. */
    private TemplateDTO validate(TemplateDTO request, UUID id) {
        if (request == null) {
            throw new ApiException(400, "Skabelonen skal være et JSON-objekt.");
        }
        return TemplateDTO.builder()
                .id(id)
                .name(requiredText(strip(request.getName()), "Navn", 150))
                .subject(requiredText(strip(request.getSubject()), "Emne", 200))
                .body(requiredText(request.getBody(), "Brødtekst", 5000))
                .build();
    }

    //--------------------------------------------------------------

    private String requiredText(String value, String label, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, label + " skal udfyldes.");
        }
        if (value.length() > maximumLength) {
            throw new ApiException(400, label + " må højst være " + maximumLength + " tegn.");
        }
        return value;
    }

    //--------------------------------------------------------------

    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
