package app.support;

import app.daos.MessageDAO;
import app.daos.UserDAO;
import app.entities.Message;
import app.entities.User;
import app.enums.Role;
import app.services.ConversationService;
import app.services.MessageEmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public abstract class MessageTestSupport {
    protected final ObjectMapper json = new ObjectMapper();
    protected EntityManagerFactory emf;
    protected EntityManager em;
    protected MessageDAO dao;
    protected ConversationService service;
    protected RecordingMessageEmailService messageEmailService;
    protected User admin;
    protected User alice;
    protected User bob;
    protected User owner;
    protected final LocalDateTime timestamp = LocalDateTime.of(2026, 10, 6, 12, 0);

    //--------------------------------------------------------------

    @BeforeEach
    void openDatabase() {
        emf = new Configuration().addAnnotatedClass(User.class).addAnnotatedClass(Message.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:message_" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false").buildSessionFactory();
        em = emf.createEntityManager();
        dao = new MessageDAO(em);
        messageEmailService = new RecordingMessageEmailService();
        service = new ConversationService(dao, Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC),
                messageEmailService);
        admin = user("Lise", "Admin", Role.ADMIN);
        alice = user("Alice", "Zebra", Role.USER);
        bob = user("Bob", "Apple", Role.USER);
        owner = user("Owner", "Owner", Role.OWNER);
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

    protected User user(String name, String company, Role... roles) {
        return new UserDAO(em).create(User.builder().firstname(name).lastname("Jensen").company(company)
                .email(UUID.randomUUID() + "@example.com").cvr("12345678").phone("12345678")
                .address("Testvej 1").city("Copenhagen").password("x".repeat(60))
                .roles(new HashSet<>(Set.of(roles))).build());
    }

    //--------------------------------------------------------------

    protected Message message(User sender, User recipient, String subject, String body, int minutes, boolean read) {
        return dao.create(Message.builder().sender(sender).recipient(recipient).subject(subject).body(body)
                .createdAt(timestamp.plusMinutes(minutes)).read(read).build());
    }

    //--------------------------------------------------------------

    protected Message reload(UUID id) {
        try (EntityManager reader = emf.createEntityManager()) {
            return reader.find(Message.class, id);
        }
    }

    //--------------------------------------------------------------

    protected long countMessages() {
        try (EntityManager reader = emf.createEntityManager()) {
            return reader.createQuery("select count(m) from Message m", Long.class).getSingleResult();
        }
    }

    //--------------------------------------------------------------

    protected static class RecordingMessageEmailService implements MessageEmailService {
        private int calls;
        private User lastRecipient;
        private Message lastMessage;
        private RuntimeException failure;

        //--------------------------------------------------------------

        @Override
        public void sendAdminMessageNotification(User recipient, Message message) {
            calls++;
            lastRecipient = recipient;
            lastMessage = message;
            if (failure != null) {
                throw failure;
            }
        }

        //--------------------------------------------------------------

        public int calls() {
            return calls;
        }

        //--------------------------------------------------------------

        public User lastRecipient() {
            return lastRecipient;
        }

        //--------------------------------------------------------------

        public Message lastMessage() {
            return lastMessage;
        }

        //--------------------------------------------------------------

        public void failWith(RuntimeException failure) {
            this.failure = failure;
        }
    }
}
