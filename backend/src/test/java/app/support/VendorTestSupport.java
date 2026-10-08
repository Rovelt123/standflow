package app.support;

import app.daos.VendorDAO;
import app.entities.Application;
import app.enums.ApplicationStandType;
import app.enums.ApplicationStatus;
import app.services.VendorService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDate;
import java.util.UUID;

/** Isolated H2 database for the vendor (Stadeholdere) tests. One fresh database per test. */
public abstract class VendorTestSupport {

    protected EntityManagerFactory emf;
    protected EntityManager em;
    protected VendorDAO dao;
    protected VendorService service;

    @BeforeEach
    void openDatabase() {
        emf = new Configuration().addAnnotatedClass(Application.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:vendor_" + UUID.randomUUID())
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        em = emf.createEntityManager();
        dao = new VendorDAO(em);
        service = new VendorService(dao);
    }

    //--------------------------------------------------------------

    @AfterEach
    void closeDatabase() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    //--------------------------------------------------------------

    /** Persists a valid application with the given company name and status. */
    protected void persistApplication(String company, ApplicationStatus status) {
        Application application = Application.builder()
                .company(company)
                .contact("Kontaktperson")
                .cvr("12345678")
                .email("kontakt@example.dk")
                .phone("12345678")
                .address("Vej 1")
                .city("4900 Maribo")
                .website("www.example.dk")
                .products("Varer fra " + company)
                .previousExhibitor(false)
                .standType(ApplicationStandType.A)
                .tables(0)
                .chairs(0)
                .status(status)
                .createdAt(LocalDate.now())
                .build();

        em.getTransaction().begin();
        em.persist(application);
        em.getTransaction().commit();
    }
}
