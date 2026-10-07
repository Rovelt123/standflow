package app.support;

import app.daos.ApplicationDAO;
import app.daos.TemplateDAO;
import app.daos.UnsubscribeTokenDAO;
import app.daos.UserDAO;
import app.entities.Application;
import app.entities.Template;
import app.entities.UnsubscribeToken;
import app.entities.User;
import app.enums.ApplicationStandType;
import app.enums.ApplicationStatus;
import app.enums.Role;
import app.services.NewsletterEmailService;
import app.services.NewsletterService;
import app.services.TemplateService;
import app.services.UnsubscribeTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class NewsletterTestSupport {
    protected final ObjectMapper json = new ObjectMapper();
    protected EntityManagerFactory emf;
    protected EntityManager em;
    protected ApplicationDAO applicationDAO;
    protected UserDAO userDAO;
    protected TemplateDAO templateDAO;
    protected UnsubscribeTokenDAO tokenDAO;
    protected UnsubscribeTokenService tokenService;
    protected NewsletterService newsletterService;
    protected RecordingNewsletterEmailService emailService;
    protected Template template;
    protected User admin;
    protected User alice;
    protected User bob;
    protected User charlie;
    protected User dave;
    protected User emptyCompany;
    protected final Clock clock = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    //--------------------------------------------------------------

    @BeforeEach
    void openDatabase() {
        emf = new Configuration()
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(Application.class)
                .addAnnotatedClass(Template.class)
                .addAnnotatedClass(UnsubscribeToken.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:newsletter_" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        em = emf.createEntityManager();
        applicationDAO = new ApplicationDAO(em);
        userDAO = new UserDAO(em);
        templateDAO = new TemplateDAO(em);
        tokenDAO = new UnsubscribeTokenDAO(em);
        tokenService = new UnsubscribeTokenService(tokenDAO, userDAO, new SecureRandom(), clock);
        emailService = new RecordingNewsletterEmailService();
        newsletterService = new NewsletterService(applicationDAO, userDAO, new TemplateService(templateDAO),
                tokenService, emailService, clock);
        seedUsers();
        seedTemplate();
        seedApplications();
    }

    //--------------------------------------------------------------

    @AfterEach
    void closeDatabase() {
        if (em != null && em.isOpen()) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
        if (emf != null && emf.isOpen()) emf.close();
    }

    //--------------------------------------------------------------

    protected void seedUsers() {
        admin = user("Lise", "Admin", "Admin", true, Role.ADMIN);
        alice = user("Alice", "Andersen", "Alice Studio", true, Role.USER);
        bob = user("Bob", "Berg", "Bob Bakery", false, Role.USER);
        charlie = user("Charlie", "Christensen", "Charlie Ceramics", true, Role.USER);
        dave = user("Dave", "Dahl", "Dave Design", true, Role.USER);
        emptyCompany = user("Empty", "Company", "", true, Role.USER);
    }

    //--------------------------------------------------------------

    protected void seedTemplate() {
        template = templateDAO.create(Template.builder()
                .name("Newsletter")
                .subject("Hej {{firstname}} {{lastname}}")
                .body("Firma: {{company}}\nEmail: {{email}}")
                .build());
    }

    //--------------------------------------------------------------

    protected void seedApplications() {
        application(alice, ApplicationStandType.A, false, LocalDate.of(2026, 2, 1));
        application(alice, ApplicationStandType.A, false, LocalDate.of(2026, 2, 2));
        application(bob, ApplicationStandType.A, false, LocalDate.of(2026, 3, 1));
        application(charlie, ApplicationStandType.B, true, LocalDate.of(2025, 5, 1));
        application(dave, ApplicationStandType.C, true, LocalDate.of(2024, 5, 1));
    }

    //--------------------------------------------------------------

    protected User user(String firstname, String lastname, String company, boolean acceptMarketing, Role... roles) {
        return userDAO.create(User.builder()
                .firstname(firstname)
                .lastname(lastname)
                .company(company)
                .email(UUID.randomUUID() + "@example.com")
                .cvr("12345678")
                .phone("12345678")
                .address("Testvej 1")
                .city("2100 Copenhagen")
                .acceptTerms(true)
                .acceptPrivacy(true)
                .acceptMarketing(acceptMarketing)
                .emailNotifications(true)
                .password("x".repeat(60))
                .roles(new HashSet<>(Set.of(roles)))
                .build());
    }

    //--------------------------------------------------------------

    protected Application application(User user, ApplicationStandType standType, boolean previousExhibitor,
                                      LocalDate createdAt) {
        return applicationDAO.create(Application.builder()
                .userId(user.getId())
                .company(user.getCompany().isBlank() ? "No Company" : user.getCompany())
                .contact(user.getFirstname() + " " + user.getLastname())
                .cvr("12345678")
                .email(user.getEmail())
                .phone("12345678")
                .address("Testvej 1")
                .city("2100 Copenhagen")
                .products("Products")
                .previousExhibitor(previousExhibitor)
                .standType(standType)
                .tables(1)
                .chairs(1)
                .status(ApplicationStatus.PENDING)
                .createdAt(createdAt)
                .build());
    }

    //--------------------------------------------------------------

    protected User reloadUser(UUID id) {
        try (EntityManager reader = emf.createEntityManager()) {
            return reader.find(User.class, id);
        }
    }

    //--------------------------------------------------------------

    protected List<UnsubscribeToken> tokensFor(User user) {
        try (EntityManager reader = emf.createEntityManager()) {
            return reader.createQuery("""
                    select t from UnsubscribeToken t
                    where t.user.id = :userId
                    order by t.createdAt asc, t.id asc
                    """, UnsubscribeToken.class)
                    .setParameter("userId", user.getId())
                    .getResultList();
        }
    }

    //--------------------------------------------------------------

    protected void expire(UnsubscribeToken token) {
        token.setExpiresAt(LocalDate.of(2026, 1, 1).atStartOfDay());
        tokenDAO.update(token);
    }

    //--------------------------------------------------------------

    protected void revoke(UnsubscribeToken token) {
        token.setRevoked(true);
        tokenDAO.update(token);
    }

    //--------------------------------------------------------------

    protected String unsubscribeTokenFrom(String body) {
        int marker = body.indexOf("token=");
        if (marker < 0) {
            throw new AssertionError("Missing unsubscribe token");
        }
        return body.substring(marker + "token=".length()).strip();
    }

    //--------------------------------------------------------------

    protected static class RecordingNewsletterEmailService implements NewsletterEmailService {
        private final List<SentNewsletter> sent = new ArrayList<>();
        private RuntimeException failure;

        //--------------------------------------------------------------

        @Override
        public void sendNewsletter(User recipient, String subject, String body) {
            if (failure != null) {
                throw failure;
            }
            sent.add(new SentNewsletter(recipient.getId(), recipient.getEmail(), subject, body));
        }

        //--------------------------------------------------------------

        public List<SentNewsletter> sent() {
            return sent;
        }

        //--------------------------------------------------------------

        public void failWith(RuntimeException failure) {
            this.failure = failure;
        }
    }

    //--------------------------------------------------------------

    protected record SentNewsletter(UUID recipientId, String email, String subject, String body) {
    }
}
