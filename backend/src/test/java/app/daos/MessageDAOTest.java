package app.daos;

import app.entities.Message;
import app.support.MessageTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageDAOTest extends MessageTestSupport {
    @Test
    void persistsRelationsAndChronologicalConversationInBothDirections() {
        Message later = message(admin, alice, "Reply", "later", 5, false);
        Message earlier = message(alice, admin, "Question", "earlier", 0, false);
        message(bob, admin, "Other", "private", 2, false);
        message(alice, bob, "Unrelated", "private", 1, false);
        assertNotNull(earlier.getId());
        Message stored = reload(earlier.getId());
        assertEquals(alice.getId(), stored.getSender().getId());
        assertEquals(admin.getId(), stored.getRecipient().getId());
        assertFalse(stored.isRead());
        assertEquals(List.of(earlier.getId(), later.getId()), dao.getConversation(alice.getId(), admin.getId())
                .stream().map(Message::getId).toList());
        assertEquals(3, dao.getAdminMessages(admin.getId()).size());
        assertEquals(List.of(admin.getId()), dao.getAdmins().stream().map(user -> user.getId()).toList());
    }

    //--------------------------------------------------------------

    @Test
    void markReadPersistsOnlyUnreadIncomingMessagesAndIsIdempotent() {
        Message incoming = message(alice, admin, "A", "new", 0, false);
        Message alreadyRead = message(alice, admin, "A", "old", 1, true);
        Message outgoing = message(admin, alice, "A", "reply", 2, false);
        Message other = message(bob, admin, "B", "other", 3, false);
        Message wrongRecipient = message(alice, bob, "C", "other recipient", 4, false);
        assertEquals(1, dao.getUnread(alice.getId(), admin.getId()).size());
        dao.markRead(alice.getId(), admin.getId());
        dao.markRead(alice.getId(), admin.getId());
        assertTrue(reload(incoming.getId()).isRead());
        assertTrue(reload(alreadyRead.getId()).isRead());
        assertFalse(reload(outgoing.getId()).isRead());
        assertFalse(reload(other.getId()).isRead());
        assertFalse(reload(wrongRecipient.getId()).isRead());
        assertTrue(dao.getUnread(alice.getId(), admin.getId()).isEmpty());
        assertTrue(dao.getConversation(alice.getId(), admin.getId()).getFirst().isRead());
    }

    //--------------------------------------------------------------

    @Test
    void requiredColumnsRejectNullValues() {
        for (String field : List.of("sender", "recipient", "subject", "body", "createdAt")) {
            try (var isolated = emf.createEntityManager()) {
                Message invalid = Message.builder().sender(isolated.find(app.entities.User.class, alice.getId()))
                        .recipient(isolated.find(app.entities.User.class, admin.getId()))
                        .subject("Subject").body("Body").createdAt(timestamp).build();
                switch (field) {
                    case "sender" -> invalid.setSender(null);
                    case "recipient" -> invalid.setRecipient(null);
                    case "subject" -> invalid.setSubject(null);
                    case "body" -> invalid.setBody(null);
                    default -> invalid.setCreatedAt(null);
                }
                assertThrows(RuntimeException.class, () -> new MessageDAO(isolated).create(invalid), field);
            }
        }
        assertEquals(0, countMessages());
    }
}
