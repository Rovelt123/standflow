package app.support;

import app.daos.ApplicationDAO;
import app.dtos.ApplicationRequestDTO;
import app.entities.Application;
import app.services.ApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ApplicationTestSupport {

    protected final ObjectMapper json = new ObjectMapper();
    protected EntityManagerFactory emf;
    protected EntityManager em;
    protected ApplicationDAO dao;
    protected ApplicationService service;
    protected final AtomicReference<ApplicationService.IntakeStatus> intake =
            new AtomicReference<>(new ApplicationService.IntakeStatus(true, null));
    protected final Clock clock = Clock.fixed(Instant.parse("2026-10-05T23:30:00Z"),
            ZoneId.of("Europe/Copenhagen"));

    @BeforeEach
    void openDatabase() {
        emf = new Configuration().addAnnotatedClass(Application.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:application_" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        em = emf.createEntityManager();
        dao = new ApplicationDAO(em);
        service = new ApplicationService(dao, intake::get, clock);
    }

    //--------------------------------------------------------------

    @AfterEach
    void closeDatabase() {
        if (em != null && em.isOpen()) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    //--------------------------------------------------------------

    protected ObjectNode validBody() {
        ObjectNode body = json.createObjectNode();
        body.put("company", " Nordic Winter Craft ApS ");
        body.put("contact", " Anna Hansen ");
        body.put("cvr", "01234567");
        body.put("email", " kontakt@example.dk ");
        body.put("phone", "+45 88 88 88 88");
        body.put("address", " Bredgade 12 ");
        body.put("city", "4900 Maribo");
        body.put("website", " www.example.dk ");
        body.put("products", " Håndlavede sweatre ");
        body.put("previousExhibitor", false);
        body.put("standType", "B");
        body.put("tables", 2);
        body.put("chairs", 4);
        return body;
    }

    //--------------------------------------------------------------

    protected ApplicationRequestDTO request(ObjectNode body) throws Exception {
        return json.treeToValue(body, ApplicationRequestDTO.class);
    }

    //--------------------------------------------------------------

    protected long countApplications() {
        try (EntityManager reader = emf.createEntityManager()) {
            return reader.createQuery("select count(a) from Application a", Long.class).getSingleResult();
        }
    }
}
