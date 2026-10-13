package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.User;
import app.exceptions.ApiException;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
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

    public boolean prepareEmailVerification(UUID id, String hash, Instant now) {
        return executeQuery(() -> {
            int updated = em.createQuery("""
                    update User u set u.emailVerificationHash = :hash,
                    u.emailVerificationExpiresAt = :expires, u.emailVerificationRequestedAt = :now
                    where u.id = :id and u.emailVerified = false
                    and (u.emailVerificationRequestedAt is null or u.emailVerificationRequestedAt < :cutoff)
                    """)
                    .setParameter("hash", hash).setParameter("expires", now.plusSeconds(86400))
                    .setParameter("now", now).setParameter("cutoff", now.minusSeconds(60))
                    .setParameter("id", id).executeUpdate();
            em.clear();
            return updated == 1;
        });
    }

    //--------------------------------------------------------------

    public User verifyEmail(String hash, Instant now) {
        return executeQuery(() -> {
            var ids = em.createQuery("select u.id from User u where u.emailVerificationHash = :hash", UUID.class)
                    .setParameter("hash", hash).getResultList();
            if (ids.isEmpty()) return null;
            int updated = em.createQuery("""
                    update User u set u.emailVerified = true,
                    u.emailVerificationHash = null, u.emailVerificationExpiresAt = null
                    where u.id = :id and u.emailVerificationHash = :hash
                    and u.emailVerified = false and u.emailVerificationExpiresAt > :now
                    """)
                    .setParameter("id", ids.getFirst()).setParameter("hash", hash)
                    .setParameter("now", now).executeUpdate();
            em.clear();
            return updated == 1 ? em.find(User.class, ids.getFirst()) : null;
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
