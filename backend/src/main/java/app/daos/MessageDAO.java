package app.daos;

import app.daos.generic.EntityManagerDAO;
import app.entities.Message;
import app.entities.User;
import app.entities.UploadedFile;
import app.enums.Role;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Consumer;

public class MessageDAO extends EntityManagerDAO<Message> {
    public MessageDAO(EntityManager em) {
        super(em, Message.class);
    }

    //--------------------------------------------------------------

    public User getUser(UUID id) {
        return executeQuery(() -> em.find(User.class, id));
    }

    //--------------------------------------------------------------

    public Message createWithAttachments(Message message, List<UUID> ids, Consumer<List<UploadedFile>> validate) {
        FileDAO files = new FileDAO(em);
        return files.inTransaction(() -> {
            List<UploadedFile> attachments = new ArrayList<>();
            // Consistent lock order prevents deadlocks between concurrent sends.
            ids.stream().sorted().forEach(id -> attachments.add(files.lock(id)));
            validate.accept(attachments);
            em.persist(message);
            attachments.forEach(file -> file.setMessage(message));
            return message;
        });
    }

    //--------------------------------------------------------------

    public List<UploadedFile> getAttachments(UUID messageId) {
        return new FileDAO(em).getForMessage(messageId);
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
