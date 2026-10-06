package app;

import app.configs.TestHibernateConfig;
import app.daos.UserDAO;
import app.daos.TemplateDAO;
import app.entities.User;
import app.enums.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import java.util.Set;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class SetupTest {

    protected static EntityManagerFactory emf;
    protected EntityManager em;

    protected UserDAO userDAO;
    protected TemplateDAO weddingDAO;

    protected User testUser;
    protected User testUser2;


    // ________________________________________________________

    @BeforeAll
    static void setupAll() {
        emf = TestHibernateConfig.getTestEmf();
    }

    // ________________________________________________________

    @AfterAll
    static void closeAll() {
        emf.close();
    }

    // ________________________________________________________

    @BeforeEach
    protected void setup() {
        em = emf.createEntityManager();
        em.getTransaction().begin();

        userDAO = new UserDAO(em);
        weddingDAO = new TemplateDAO(em);

        setupUserTest();
    }

    // ________________________________________________________

    protected void setupUserTest(){

        testUser = User.builder()
                .firstname("John")
                .lastname("Doe")
                .roles(Set.of(Role.USER))
                .email("john123@test.dk")
                .password("123")
                .build();

        testUser2 = User.builder()
                .firstname("Gert")
                .lastname("Hansen")
                .roles(Set.of(Role.USER))
                .email("testuser2@test.dk")
                .password("123")
                .build();
    }

    // ________________________________________________________

    @AfterEach
    protected void cleanUp() {
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.close();
    }
}
