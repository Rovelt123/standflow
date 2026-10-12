package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.UploadedFile;
import app.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class FileDAO extends EntityManagerDAO<UploadedFile> {
    public FileDAO(EntityManager em) {
        super(em, UploadedFile.class);
    }

    //--------------------------------------------------------------

    public <T> T inTransaction(Supplier<T> work) {
        return executeQuery(work);
    }

    //--------------------------------------------------------------

    @Override
    protected <T> T executeQuery(Supplier<T> work) {
        synchronized (em) {
            boolean owner = !em.getTransaction().isActive();
            try {
                if (owner) em.getTransaction().begin();
                T result = work.get();
                if (owner) em.getTransaction().commit();
                return result;
            } catch (RuntimeException exception) {
                if (owner) {
                    if (em.getTransaction().isActive()) em.getTransaction().rollback();
                    em.clear();
                }
                throw exception;
            }
        }
    }

    //--------------------------------------------------------------

    public User getUser(UUID id) {
        return executeQuery(() -> em.find(User.class, id));
    }

    //--------------------------------------------------------------

    public UploadedFile findForDownload(UUID id) {
        return executeQuery(() -> em.createQuery("""
                select f from UploadedFile f
                join fetch f.uploadedBy
                left join fetch f.message m
                left join fetch m.sender
                left join fetch m.recipient
                where f.id = :id
                """, UploadedFile.class).setParameter("id", id).getResultList().stream().findFirst().orElse(null));
    }

    //--------------------------------------------------------------

    public List<User> getAdmins() {
        return new MessageDAO(em).getAdmins();
    }

    //--------------------------------------------------------------

    public List<UploadedFile> createAll(List<UploadedFile> files) {
        return executeQuery(() -> {
            files.forEach(em::persist);
            return files;
        });
    }

    //--------------------------------------------------------------

    public UploadedFile lock(UUID id) {
        UploadedFile file = em.find(UploadedFile.class, id, LockModeType.PESSIMISTIC_WRITE);
        // A long-lived EntityManager may have cached an older pending state.
        if (file != null) em.refresh(file, LockModeType.PESSIMISTIC_WRITE);
        return file;
    }

    //--------------------------------------------------------------

    public List<UploadedFile> getForMessage(UUID messageId) {
        return executeQuery(() -> em.createQuery("""
                select f from UploadedFile f where f.message.id = :message
                order by f.uploadedAt, f.id
                """, UploadedFile.class).setParameter("message", messageId).getResultList());
    }

    //--------------------------------------------------------------

    public List<UUID> getExpiredIds(Instant cutoff) {
        return executeQuery(() -> em.createQuery("""
                select f.id from UploadedFile f where f.message is null and f.uploadedAt <= :cutoff
                order by f.id
                """, UUID.class).setParameter("cutoff", cutoff).setMaxResults(100).getResultList());
    }
}
