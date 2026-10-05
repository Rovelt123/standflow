package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.Application;
import app.exceptions.ApiException;
import jakarta.persistence.EntityManager;

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
}
