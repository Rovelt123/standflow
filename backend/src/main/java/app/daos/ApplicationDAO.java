package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.Application;
import app.enums.ApplicationStandType;
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

    //--------------------------------------------------------------

    public List<Application> getByMarketYear(Integer year) {
        return executeQuery(() -> year == null
                ? em.createQuery("select a from Application a where a.marketYear is null order by a.createdAt desc", Application.class).getResultList()
                : em.createQuery("select a from Application a where a.marketYear = :year order by a.createdAt desc", Application.class)
                    .setParameter("year", year).getResultList());
    }

    //--------------------------------------------------------------

    public List<Application> getParticipationHistory(UUID userId, int beforeYear) {
        if (userId == null) return List.of();
        return executeQuery(() -> em.createQuery("select a from Application a where a.userId = :userId "
                        + "and a.attended = true and a.marketYear < :year order by a.marketYear desc, a.id", Application.class)
                .setParameter("userId", userId).setParameter("year", beforeYear).getResultList());
    }

    //--------------------------------------------------------------

    public List<Application> getReusableApplications(UUID userId, int currentYear) {
        if (userId == null) return List.of();
        return executeQuery(() -> em.createQuery("select a from Application a where a.userId = :userId "
                        + "and a.status = :status and (a.marketYear is null or a.marketYear <= :year) "
                        + "order by a.marketYear desc nulls last, a.createdAt desc, a.id", Application.class)
                .setParameter("userId", userId).setParameter("status", app.enums.ApplicationStatus.ACCEPTED)
                .setParameter("year", currentYear).getResultList());
    }

    //--------------------------------------------------------------

    public record StandTypeUpdate(Application application, ApplicationStandType previousType) { }

    //--------------------------------------------------------------

    public StandTypeUpdate updateStandType(UUID id, ApplicationStandType standType) {
        synchronized (em) {
            try {
                return executeQuery(() -> {
                    Application application = em.find(Application.class, id);
                    if (application == null) return null;
                    em.refresh(application, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                    ApplicationStandType previous = application.getStandType();
                    application.setStandType(standType);
                    return new StandTypeUpdate(application, previous);
                });
            } catch (RuntimeException exception) {
                em.clear();
                throw exception;
            }
        }
    }
}
