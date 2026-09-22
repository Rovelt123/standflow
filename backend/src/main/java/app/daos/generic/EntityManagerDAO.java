package app.daos.generic;

import app.exceptions.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;

import java.util.List;
import java.util.function.Supplier;

public class EntityManagerDAO<T> implements IDAO<T> {

    protected final EntityManager em;
    protected Class<T> classSpecific;

    // ________________________________________________________

    protected EntityManagerDAO(EntityManager em, Class<T> entityClass){
        this.em = em;
        this.classSpecific = entityClass;
    }

    // ________________________________________________________

    @Override
    public T create(T t) {
        return executeQuery(() -> {
            if (!em.contains(t)) {
                em.persist(t);
            }
            return t;
        });
    }

    // ________________________________________________________

    @Override
    public T update(T t) {
        return executeQuery(() -> em.merge(t));
    }

    // ________________________________________________________

    @Override
    public T delete(T t) {
        return executeQuery(() -> {
            T managed = em.contains(t) ? t : em.merge(t);
            em.remove(managed);
            return managed;
        });
    }

    // ________________________________________________________

    @Override
    public T deleteById(Object id) {
        return executeQuery(() -> {
            T entity = findById(id);
            if (entity != null) {
                em.remove(entity);
            }
            return entity;
        });
    }

    // ________________________________________________________

    @Override
    public T getById(Object id) {
        return executeQuery(() -> findById(id));
    }

    // ________________________________________________________

    @Override
    public boolean existByColumn(Object value, String column) {
        String JPQL = "SELECT COUNT(x) FROM " + classSpecific.getSimpleName() + " x WHERE x." + column + " = :value";
        Long count = executeQuery(() -> em.createQuery(JPQL, Long.class)
                .setParameter("value", value)
                .getSingleResult());
        return count != null && count > 0;
    }

    // ________________________________________________________

    @Override
    public List<T> getAll() {
        return executeQuery(() -> {
            String JPQL = "SELECT x FROM " + classSpecific.getSimpleName() + " x";
            return em.createQuery(JPQL, classSpecific)
                    .getResultList();
        });
    }

    // ________________________________________________________

    @Override
    public void deleteAll() {
        executeQuery(() -> {
            List<T> entities = getAll();
            for (T entity : entities) {
                em.remove(entity);
            }
            return null;
        });
    }

    // ________________________________________________________

    protected <R> R executeQuery(Supplier<R> query) {
        synchronized (em) {
            boolean startedTransaction = false;
            try {
                if (!em.getTransaction().isActive()) {
                    em.getTransaction().begin();
                    startedTransaction = true;
                }
                R result = query.get();
                if (startedTransaction) {
                    em.getTransaction().commit();
                }
                return result;
            } catch (NoResultException e){
                if (startedTransaction && em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                return null;
            } catch (RuntimeException e) {
                if (startedTransaction && em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }

                if (e instanceof PersistenceException) {
                    throw e;
                }

                throw new ApiException(500, e.getMessage());
            }
        }
    }

    // ________________________________________________________

    private T findById(Object id) {
        return em.find(classSpecific, id);
    }

}
