package app.services;

import app.daos.ApplicationDAO;
import app.daos.UserDAO;
import app.dtos.NewsletterRequest;
import app.dtos.NewsletterResponse;
import app.entities.Application;
import app.entities.Template;
import app.entities.User;
import app.enums.ApplicationStandType;
import app.enums.NewsletterAudience;
import app.exceptions.ApiException;
import app.server.Setup;
import app.utils.ErrorHandler;
import app.utils.Utils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class NewsletterService {

    private static final Set<String> NO_SELECTOR_AUDIENCES = Set.of(
            NewsletterAudience.NEW_STALLHOLDERS.name(),
            NewsletterAudience.PREVIOUS_YEAR_STALLHOLDERS.name(),
            NewsletterAudience.ALL_PREVIOUS_STALLHOLDERS.name(),
            NewsletterAudience.ALL_APPLICANTS.name());
    private static final String FRONTEND_URL_PROPERTY = "FRONTEND_URL";

    private final ApplicationDAO applicationDAO;
    private final UserDAO userDAO;
    private final TemplateService templateService;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final NewsletterEmailService emailService;
    private final Clock clock;

    //--------------------------------------------------------------

    public NewsletterService() {
        this(new ApplicationDAO(Setup.em), new UserDAO(Setup.em), new TemplateService(),
                new UnsubscribeTokenService(), new GmailSmtpNewsletterEmailService(), Clock.systemDefaultZone());
    }

    //--------------------------------------------------------------

    public NewsletterService(ApplicationDAO applicationDAO, UserDAO userDAO, TemplateService templateService,
                             UnsubscribeTokenService unsubscribeTokenService,
                             NewsletterEmailService emailService, Clock clock) {
        this.applicationDAO = Objects.requireNonNull(applicationDAO);
        this.userDAO = Objects.requireNonNull(userDAO);
        this.templateService = Objects.requireNonNull(templateService);
        this.unsubscribeTokenService = Objects.requireNonNull(unsubscribeTokenService);
        this.emailService = Objects.requireNonNull(emailService);
        this.clock = Objects.requireNonNull(clock);
    }

    //--------------------------------------------------------------

    public NewsletterResponse send(NewsletterRequest request) {
        NewsletterAudience audience = audience(request);
        validateSelectorCombination(request, audience);
        Template template = template(request);
        List<User> recipients = recipients(request, audience);

        int sentTo = 0;
        int skippedNoConsent = 0;
        int failedToSend = 0;
        for (User recipient : recipients) {
            if (!recipient.isAcceptMarketing()) {
                skippedNoConsent++;
                continue;
            }
            String unsubscribeLink = unsubscribeLink(unsubscribeTokenService.issueToken(recipient));
            String subject = render(template.getSubject(), recipient);
            String body = appendUnsubscribe(render(template.getBody(), recipient), unsubscribeLink);
            try {
                emailService.sendNewsletter(recipient, subject, body);
                sentTo++;
            } catch (RuntimeException exception) {
                failedToSend++;
            }
        }
        return NewsletterResponse.builder()
                .sentTo(sentTo)
                .skippedNoConsent(skippedNoConsent)
                .failedToSend(failedToSend)
                .build();
    }

    //--------------------------------------------------------------

    public User unsubscribe(Map<String, ?> body) {
        return unsubscribeTokenService.unsubscribe(body);
    }

    //--------------------------------------------------------------

    private NewsletterAudience audience(NewsletterRequest request) {
        if (request == null) {
            throw new ApiException(400, "Nyhedsbrevet skal vaere et JSON-objekt.");
        }
        if (request.getAudience() == null || request.getAudience().isBlank()) {
            throw new ApiException(400, "Audience skal udfyldes.");
        }
        return ErrorHandler.tryParseEnum(NewsletterAudience.class, request.getAudience(), "Ugyldig audience.");
    }

    //--------------------------------------------------------------

    private Template template(NewsletterRequest request) {
        if (request.getTemplateId() == null) {
            throw new ApiException(400, "templateId skal udfyldes.");
        }
        return templateService.getById(request.getTemplateId());
    }

    //--------------------------------------------------------------

    private void validateSelectorCombination(NewsletterRequest request, NewsletterAudience audience) {
        if (audience == NewsletterAudience.INDIVIDUAL) {
            if (request.getRecipientIds() == null || request.getRecipientIds().isEmpty()) {
                throw new ApiException(400, "recipientIds skal udfyldes for INDIVIDUAL.");
            }
            if (request.getCategory() != null) {
                throw new ApiException(400, "category maa kun bruges for CATEGORY.");
            }
            return;
        }
        if (audience == NewsletterAudience.CATEGORY) {
            if (request.getCategory() == null || request.getCategory().isBlank()) {
                throw new ApiException(400, "category skal udfyldes for CATEGORY.");
            }
            if (request.getRecipientIds() != null) {
                throw new ApiException(400, "recipientIds maa kun bruges for INDIVIDUAL.");
            }
            return;
        }
        if (NO_SELECTOR_AUDIENCES.contains(audience.name())) {
            if (request.getRecipientIds() != null || request.getCategory() != null) {
                throw new ApiException(400, "Audience accepterer ikke ekstra modtagerfelter.");
            }
        }
    }

    //--------------------------------------------------------------

    private List<User> recipients(NewsletterRequest request, NewsletterAudience audience) {
        Map<UUID, User> recipients = new LinkedHashMap<>();
        switch (audience) {
            case INDIVIDUAL -> request.getRecipientIds().forEach(id -> addRequiredUser(recipients, id));
            case CATEGORY -> addApplicationUsers(recipients, applicationsByCategory(request.getCategory()));
            case NEW_STALLHOLDERS -> addApplicationUsers(recipients, applicationDAO.getAll().stream()
                    .filter(application -> !application.isPreviousExhibitor()).toList());
            case PREVIOUS_YEAR_STALLHOLDERS -> addApplicationUsers(recipients, previousYearApplications());
            case ALL_PREVIOUS_STALLHOLDERS -> addApplicationUsers(recipients, applicationDAO.getAll().stream()
                    .filter(Application::isPreviousExhibitor).toList());
            case ALL_APPLICANTS -> addApplicationUsers(recipients, applicationDAO.getAll());
        }
        return recipients.values().stream().toList();
    }

    //--------------------------------------------------------------

    private List<Application> applicationsByCategory(String category) {
        ApplicationStandType standType = ErrorHandler.tryParseEnum(ApplicationStandType.class, category,
                "Ugyldig category.");
        return applicationDAO.getAll().stream()
                .filter(application -> application.getStandType() == standType)
                .toList();
    }

    //--------------------------------------------------------------

    private List<Application> previousYearApplications() {
        int currentYear = LocalDate.now(clock).getYear();
        LocalDate start = LocalDate.of(currentYear - 1, 1, 1);
        LocalDate end = LocalDate.of(currentYear, 1, 1);
        return applicationDAO.getAll().stream()
                .filter(application -> application.getCreatedAt() != null)
                .filter(application -> !application.getCreatedAt().isBefore(start)
                        && application.getCreatedAt().isBefore(end))
                .toList();
    }

    //--------------------------------------------------------------

    private void addApplicationUsers(Map<UUID, User> recipients, List<Application> applications) {
        for (Application application : applications) {
            UUID userId = application.getUserId();
            if (userId == null || recipients.containsKey(userId)) {
                continue;
            }
            User user = userDAO.getById(userId);
            if (user != null) {
                recipients.put(user.getId(), user);
            }
        }
    }

    //--------------------------------------------------------------

    private void addRequiredUser(Map<UUID, User> recipients, UUID id) {
        if (id == null) {
            throw new ApiException(400, "recipientIds maa ikke indeholde null.");
        }
        User user = ErrorHandler.tryEntity(userDAO.getById(id), "Modtageren findes ikke.");
        recipients.putIfAbsent(user.getId(), user);
    }

    //--------------------------------------------------------------

    private String render(String text, User user) {
        return value(text)
                .replace("<Firstname>", value(user.getFirstname()))
                .replace("<Lastname>", value(user.getLastname()))
                .replace("<Company>", value(user.getCompany()))
                .replace("<Email>", value(user.getEmail()));
    }

    //--------------------------------------------------------------

    private String appendUnsubscribe(String body, String unsubscribeLink) {
        return body + "\n\nAfmeld nyhedsbreve: " + unsubscribeLink;
    }

    //--------------------------------------------------------------

    private String unsubscribeLink(String token) {
        return frontendUrl() + "/unsubscribe?token=" + token;
    }

    //--------------------------------------------------------------

    private String frontendUrl() {
        String value = Utils.getPropertyValue(FRONTEND_URL_PROPERTY, "config.properties");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    //--------------------------------------------------------------

    private String value(String value) {
        return value == null ? "" : value;
    }
}
