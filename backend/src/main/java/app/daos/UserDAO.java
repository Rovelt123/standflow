package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.User;
import app.exceptions.ApiException;
import jakarta.persistence.EntityManager;

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
}
