package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.User;
import app.exceptions.ApiException;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.UUID;

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

    public void deleteAccount(User user) {
        executeQuery(() -> {
            em.createQuery("delete from Application a where a.userId = :userId")
                    .setParameter("userId", user.getId()).executeUpdate();
            em.remove(em.contains(user) ? user : em.merge(user));
            return null;
        });
    }

    //--------------------------------------------------------------

    public boolean preparePasswordReset(UUID id, String hash, Instant now) {
        return executeQuery(() -> {
            int updated = em.createQuery("""
                    update User u set u.passwordResetHash = :hash,
                    u.passwordResetExpiresAt = :expires, u.passwordResetRequestedAt = :now
                    where u.id = :id and (u.passwordResetRequestedAt is null or u.passwordResetRequestedAt < :cutoff)
                    """)
                    .setParameter("hash", hash).setParameter("expires", now.plusSeconds(1800))
                    .setParameter("now", now).setParameter("cutoff", now.minusSeconds(60))
                    .setParameter("id", id).executeUpdate();
            em.clear();
            return updated == 1;
        });
    }

    //--------------------------------------------------------------

    public boolean resetPassword(String hash, String password, Instant now) {
        return executeQuery(() -> {
            int updated = em.createQuery("""
                    update User u set u.password = :password, u.tokenVersion = u.tokenVersion + 1,
                    u.passwordResetHash = null, u.passwordResetExpiresAt = null
                    where u.passwordResetHash = :hash and u.passwordResetExpiresAt > :now
                    """)
                    .setParameter("password", password).setParameter("hash", hash)
                    .setParameter("now", now).executeUpdate();
            em.clear();
            return updated == 1;
        });
    }
}
