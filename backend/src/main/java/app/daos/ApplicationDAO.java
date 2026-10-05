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

    @Override
    public Application create(Application application) {
        synchronized (em) {
            // A receipt must never be returned for an uncommitted outer transaction.
            if (em.getTransaction().isActive()) {
                throw new ApiException(500, "Ansøgningen kunne ikke gemmes.");
            }
            try {
                return super.create(application);
            } catch (RuntimeException exception) {
                em.clear();
                throw new ApiException(500, "Ansøgningen kunne ikke gemmes.");
            }
        }
    }
}
