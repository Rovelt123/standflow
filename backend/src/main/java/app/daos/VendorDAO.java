package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.Application;
import app.enums.ApplicationStatus;
import jakarta.persistence.EntityManager;

import java.util.List;

/** Read-only access to accepted applications for the public Stadeholdere page. */
public class VendorDAO extends EntityManagerDAO<Application> {

    public VendorDAO(EntityManager em) {
        super(em, Application.class);
    }

    //--------------------------------------------------------------

    public List<Application> findAccepted(int offset, int limit) {
        return em.createQuery(
                        "select a from Application a where a.status = :status order by a.company asc",
                        Application.class)
                .setParameter("status", ApplicationStatus.ACCEPTED)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    //--------------------------------------------------------------

    public long countAccepted() {
        return em.createQuery(
                        "select count(a) from Application a where a.status = :status",
                        Long.class)
                .setParameter("status", ApplicationStatus.ACCEPTED)
                .getSingleResult();
    }
}
