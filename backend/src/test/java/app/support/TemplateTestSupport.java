package app.support;

import app.daos.TemplateDAO;
import app.dtos.TemplateDTO;
import app.entities.Template;
import app.services.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

public abstract class TemplateTestSupport {

    protected final ObjectMapper json = new ObjectMapper();
    protected EntityManagerFactory emf;
    protected EntityManager em;
    protected TemplateDAO dao;
    protected TemplateService service;

    @BeforeEach
    void openDatabase() {
        emf = new Configuration().addAnnotatedClass(Template.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:template_" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        em = emf.createEntityManager();
        dao = new TemplateDAO(em);
        service = new TemplateService(dao);
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
        body.put("name", " Kvittering ");
        body.put("subject", " Tak for din ansøgning ");
        body.put("body", "Kære <navn>,\n\nTak for din ansøgning.");
        return body;
    }

    //--------------------------------------------------------------

    protected TemplateDTO request(ObjectNode body) throws Exception {
        return json.treeToValue(body, TemplateDTO.class);
    }

    //--------------------------------------------------------------

    protected long countTemplates() {
        try (EntityManager reader = emf.createEntityManager()) {
            return reader.createQuery("select count(t) from Template t", Long.class).getSingleResult();
        }
    }

    //--------------------------------------------------------------

    protected Template reload(UUID id) {
        try (EntityManager reader = emf.createEntityManager()) {
            return reader.find(Template.class, id);
        }
    }
}
