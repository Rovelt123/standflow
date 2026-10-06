package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.Application;
import app.exceptions.ApiException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;

public class ApplicationDAO extends EntityManagerDAO<Application> {

    public ApplicationDAO(EntityManager em) {
        super(em, Application.class);
    }

    //--------------------------------------------------------------

    public Application createApplication(Application application) {
        synchronized (em) {
            if (em.getTransaction().isActive()) {
                throw new ApiException(500, "Ansøgningen kunne ikke gemmes.");
            }
            try {
                return create(application);
            } catch (RuntimeException exception) {
                em.clear();
                throw new ApiException(500, "Ansøgningen kunne ikke gemmes.");
            }
        }
    }

    //--------------------------------------------------------------

    public List<Application> getByUserId(UUID userId) {
        return executeQuery(() -> em.createQuery(
                "select a from Application a where a.userId = :userId order by a.createdAt desc, a.id", Application.class)
                .setParameter("userId", userId).getResultList());
    }
}
