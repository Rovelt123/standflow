package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.Message;
import app.entities.User;
import app.enums.Role;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

public class MessageDAO extends EntityManagerDAO<Message> {
    public MessageDAO(EntityManager em) {
        super(em, Message.class);
    }

    //--------------------------------------------------------------

    public User getUser(UUID id) {
        return executeQuery(() -> em.find(User.class, id));
    }

    //--------------------------------------------------------------

    public List<User> getAdmins() {
        return executeQuery(() -> em.createQuery(
                "select u from User u where :role member of u.roles", User.class)
                .setParameter("role", Role.ADMIN).getResultList());
    }

    //--------------------------------------------------------------

    public List<Message> getConversation(UUID customerId, UUID adminId) {
        return executeQuery(() -> em.createQuery("""
                select m from Message m
                join fetch m.sender join fetch m.recipient
                where (m.sender.id = :customer and m.recipient.id = :admin)
                   or (m.sender.id = :admin and m.recipient.id = :customer)
                order by m.createdAt asc, m.id asc
                """, Message.class).setParameter("customer", customerId)
                .setParameter("admin", adminId).getResultList());
    }

    //--------------------------------------------------------------

    public List<Message> getAdminMessages(UUID adminId) {
        return executeQuery(() -> em.createQuery("""
                select m from Message m
                join fetch m.sender join fetch m.recipient
                where m.sender.id = :admin or m.recipient.id = :admin
                order by m.createdAt asc, m.id asc
                """, Message.class).setParameter("admin", adminId).getResultList());
    }

    //--------------------------------------------------------------

    public List<Message> getUnread(UUID customerId, UUID adminId) {
        return executeQuery(() -> em.createQuery("""
                select m from Message m
                where m.sender.id = :customer and m.recipient.id = :admin and m.read = false
                """, Message.class).setParameter("customer", customerId)
                .setParameter("admin", adminId).getResultList());
    }

    //--------------------------------------------------------------

    public void markRead(UUID customerId, UUID adminId) {
        executeQuery(() -> {
            getUnread(customerId, adminId).forEach(message -> message.setRead(true));
            return null;
        });
    }
}
