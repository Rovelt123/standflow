package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.UnsubscribeToken;
import app.entities.User;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;

public class UnsubscribeTokenDAO extends EntityManagerDAO<UnsubscribeToken> {

    public UnsubscribeTokenDAO(EntityManager em) {
        super(em, UnsubscribeToken.class);
    }

    //--------------------------------------------------------------

    public void revokeActiveForUser(User user) {
        executeQuery(() -> {
            em.createQuery("""
                    update UnsubscribeToken t
                    set t.revoked = true
                    where t.user.id = :userId and t.revoked = false
                    """)
                    .setParameter("userId", user.getId())
                    .executeUpdate();
            return null;
        });
    }

    //--------------------------------------------------------------

    public UnsubscribeToken getUsableToken(String tokenHash, LocalDateTime now) {
        return executeQuery(() -> em.createQuery("""
                select t from UnsubscribeToken t
                join fetch t.user
                where t.tokenHash = :tokenHash and t.revoked = false and t.expiresAt > :now
                """, UnsubscribeToken.class)
                .setParameter("tokenHash", tokenHash)
                .setParameter("now", now)
                .getSingleResult());
    }

    //--------------------------------------------------------------

    public LocalDateTime latestCreatedAtForUser(User user) {
        return executeQuery(() -> em.createQuery("""
                select max(t.createdAt) from UnsubscribeToken t
                where t.user.id = :userId
                """, LocalDateTime.class)
                .setParameter("userId", user.getId())
                .getSingleResult());
    }
}
