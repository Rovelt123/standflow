package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.User;
import app.exceptions.ApiException;
import jakarta.persistence.EntityManager;

import java.util.List;

public class UserDAO extends EntityManagerDAO<User> {
    public UserDAO(EntityManager em) {
        super(em, User.class);
    }

    //--------------------------------------------------------------

    public User getByEmail(String email) {
        return executeQuery(() -> em.createQuery("select u from User u where u.email = :email", User.class)
                .setParameter("email", email).getSingleResult());
    }

    //--------------------------------------------------------------

    @Override
    public User create(User user) {
        synchronized (em) {
            if (em.getTransaction().isActive()) {
                throw new ApiException(500, "Brugeren kunne ikke oprettes.");
            }
            try {
                return super.create(user);
            } catch (RuntimeException exception) {
                em.clear();
                if (getByEmail(user.getEmail()) != null) {
                    throw new ApiException(409, "E-mailadressen er allerede registreret.");
                }
                throw new ApiException(500, "Brugeren kunne ikke oprettes.");
            }
        }
    }

    //--------------------------------------------------------------

    public List<String> deleteAccount(User user) {
        return executeQuery(() -> {
            em.flush();
            List<String> storageKeys = em.createQuery("""
                    select f.storageKey from UploadedFile f
                    left join f.message m
                    where f.uploadedBy = :user or m.sender = :user or m.recipient = :user
                    """, String.class).setParameter("user", user).getResultList();
            if (!storageKeys.isEmpty()) {
                em.createQuery("delete from UploadedFile f where f.storageKey in :storageKeys")
                        .setParameter("storageKeys", storageKeys).executeUpdate();
            }
            em.createQuery("delete from Message m where m.sender.id = :userId or m.recipient.id = :userId")
                    .setParameter("userId", user.getId()).executeUpdate();
            em.createQuery("delete from UnsubscribeToken t where t.user.id = :userId")
                    .setParameter("userId", user.getId()).executeUpdate();
            em.createQuery("delete from Application a where a.userId = :userId")
                    .setParameter("userId", user.getId()).executeUpdate();
            // Bulk deletes leave cached entities behind; reload the user before removing it.
            em.clear();
            em.remove(em.find(User.class, user.getId()));
            return storageKeys;
        });
    }
}
