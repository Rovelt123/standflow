package app.services;

import app.daos.TemplateDAO;
import app.dtos.TemplateDTO;
import app.entities.Template;
import app.exceptions.ApiException;
import app.mappers.TemplateMapper;
import app.server.Setup;
import app.utils.ErrorHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateService {

    /** The variables Lise may use in a template. Rendered with the recipient's data. */
    public static final Set<String> KNOWN_VARIABLES = Set.of("Firstname", "Lastname", "Company", "Email");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("<([^<>]+)>");

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

    /** Deletes a template permanently. A missing id gives 404 via getById. */
    public void delete(UUID id) {
        getById(id);
        try {
            templateDAO.deleteById(id);
        } catch (RuntimeException exception) {
            throw new ApiException(500, "Skabelonen kunne ikke slettes.");
        }
    }

    //--------------------------------------------------------------

    /**
     * Renders a saved template with the recipient's values. Reads only, so the stored
     * template is never changed. Unknown variables are left untouched and reported, so
     * the frontend can warn before the mail is sent.
     */
    public RenderedTemplate render(UUID id, Map<String, String> values) {
        Template template = getById(id);
        Map<String, String> safeValues = values == null ? Map.of() : values;
        String subject = substitute(template.getSubject(), safeValues);
        String body = substitute(template.getBody(), safeValues);
        List<String> unknown = unknownVariables(template.getSubject());
        for (String variable : unknownVariables(template.getBody())) {
            if (!unknown.contains(variable)) {
                unknown.add(variable);
            }
        }
        return new RenderedTemplate(subject, body, unknown);
    }

    //--------------------------------------------------------------

    /** Returns the variables used in the text that are not recognised, as written (e.g. "<Fornvan>"). */
    public List<String> unknownVariables(String text) {
        List<String> unknown = new ArrayList<>();
        if (text == null) {
            return unknown;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            String token = "<" + name + ">";
            if (!KNOWN_VARIABLES.contains(name) && !unknown.contains(token)) {
                unknown.add(token);
            }
        }
        return unknown;
    }

    //--------------------------------------------------------------

    private String substitute(String text, Map<String, String> values) {
        if (text == null) {
            return null;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = KNOWN_VARIABLES.contains(name)
                    ? values.getOrDefault(name, "")
                    : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    //--------------------------------------------------------------

    /** The id is server/path controlled; placeholders such as <Firstname> in the body are stored raw. */
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

    //--------------------------------------------------------------

    /** Result of rendering a template: the filled subject and body, plus any unknown variables found. */
    public record RenderedTemplate(String subject, String body, List<String> unknownVariables) {
    }
}
