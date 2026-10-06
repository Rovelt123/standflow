package app.services;

import app.dtos.MessageDTO;
import app.dtos.ThreadDTO;
import app.entities.Message;
import app.enums.Role;
import app.exceptions.ApiException;
import app.mappers.MessageMapper;
import app.support.MessageTestSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationServiceTest extends MessageTestSupport {
    @Test
    void customerSendsServerControlledMessageAndSeesOnlyOwnConversation() {
        MessageDTO saved = service.sendCustomer(alice.getId(), Map.of("subject", "Question", "body", "Hello"));
        service.sendCustomer(bob.getId(), Map.of("subject", "Private", "body", "Secret"));
        assertEquals(alice.getId(), saved.getSenderId());
        assertEquals(admin.getId(), saved.getRecipientId());
        assertEquals(timestamp.toString(), saved.getCreatedAt());
        assertFalse(saved.isRead());
        assertEquals(List.of(saved.getId()), service.getMine(alice.getId()).stream().map(MessageDTO::getId).toList());
        assertEquals(1, service.getThread(admin.getId(), alice.getId()).size());
        assertFalse(reload(saved.getId()).isRead());
        MessageMapper mapper = new MessageMapper();
        assertEquals(saved.getSenderId(), mapper.toEntity(saved).getSender().getId());
        assertEquals(saved.getCreatedAt(), mapper.toDTO(mapper.toEntity(saved)).getCreatedAt());
    }

    //--------------------------------------------------------------

    @Test
    void adminInitiatesAndRepliesWithLatestSubjectWithoutClearingUnread() {
        MessageDTO first = service.sendAdmin(admin.getId(), alice.getId(), Map.of("subject", "Welcome", "body", "Hello"));
        assertEquals(admin.getId(), first.getSenderId());
        assertEquals(alice.getId(), first.getRecipientId());
        assertFalse(first.isRead());
        assertEquals(1, service.getMine(alice.getId()).size());
        assertFalse(service.getThreads(admin.getId(), null).getFirst().isUnread());
        Message question = message(alice, admin, "New question", "Question", 1, false);
        service = new ConversationService(dao, java.time.Clock.fixed(
                timestamp.plusMinutes(2).toInstant(java.time.ZoneOffset.UTC), java.time.ZoneOffset.UTC));
        MessageDTO reply = service.sendAdmin(admin.getId(), alice.getId(), Map.of("body", "Answer"));
        assertEquals("New question", reply.getSubject());
        assertEquals("Answer", service.getThreads(admin.getId(), null).getFirst().getLastMessage());
        assertTrue(service.getThreads(admin.getId(), null).getFirst().isUnread());
        service.markRead(admin.getId(), alice.getId());
        assertTrue(reload(question.getId()).isRead());
        assertFalse(reload(first.getId()).isRead());
        assertFalse(reload(reply.getId()).isRead());
        assertFalse(service.getThreads(admin.getId(), null).getFirst().isUnread());
    }

    //--------------------------------------------------------------

    @Test
    void overviewGroupsLatestMessagesAndSupportsEverySort() {
        message(alice, admin, "Old", "old", 0, true);
        message(admin, alice, "Zulu", "latest Alice", 3, false);
        message(bob, admin, "Alpha", "latest Bob", 2, false);
        user("Empty", "Empty", Role.USER);
        message(owner, admin, "Not a customer", "hidden", 4, false);
        assertEquals(List.of(alice.getId(), bob.getId()), ids("name"));
        assertEquals(List.of(bob.getId(), alice.getId()), ids("company"));
        assertEquals(List.of(bob.getId(), alice.getId()), ids("subject"));
        assertEquals(List.of(alice.getId(), bob.getId()), ids("date"));
        assertEquals(ids("date"), ids(null));
        ThreadDTO latest = service.getThreads(admin.getId(), null).getFirst();
        assertEquals("Alice Jensen", latest.getCustomerName());
        assertEquals("Zebra", latest.getCompany());
        assertEquals("latest Alice", latest.getLastMessage());
        assertEquals(timestamp.plusMinutes(3).toString(), latest.getLastMessageAt());
        assertFalse(latest.isUnread());
        assertTrue(service.getThreads(admin.getId(), null).getLast().isUnread());
        error(400, () -> service.getThreads(admin.getId(), "invalid"));
        error(400, () -> service.getThreads(admin.getId(), ""));
    }

    //--------------------------------------------------------------

    @Test
    void emptyThreadsAndInvalidRecipientsHaveDefinedBehavior() {
        assertTrue(service.getMine(alice.getId()).isEmpty());
        assertTrue(service.getThreads(admin.getId(), null).isEmpty());
        assertTrue(service.getThread(admin.getId(), alice.getId()).isEmpty());
        service.markRead(admin.getId(), alice.getId());
        error(404, () -> service.getThread(admin.getId(), UUID.randomUUID()));
        error(404, () -> service.sendAdmin(admin.getId(), UUID.randomUUID(), Map.of("body", "b")));
        error(404, () -> service.markRead(admin.getId(), UUID.randomUUID()));
        for (UUID id : List.of(admin.getId(), owner.getId())) {
            error(400, () -> service.getThread(admin.getId(), id));
            error(400, () -> service.sendAdmin(admin.getId(), id, Map.of("body", "b")));
            error(400, () -> service.markRead(admin.getId(), id));
        }
    }

    //--------------------------------------------------------------

    @Test
    void persistedRolesProtectCustomerAndAdminOperations() {
        for (UUID id : List.of(alice.getId(), owner.getId())) {
            error(403, () -> service.getThreads(id, null));
            error(403, () -> service.getThread(id, bob.getId()));
            error(403, () -> service.sendAdmin(id, bob.getId(), Map.of("subject", "s", "body", "b")));
            error(403, () -> service.markRead(id, bob.getId()));
        }
        admin.getRoles().add(Role.USER);
        new app.daos.UserDAO(em).update(admin);
        for (UUID id : List.of(admin.getId(), owner.getId())) {
            error(403, () -> service.getMine(id));
            error(403, () -> service.sendCustomer(id, Map.of("subject", "s", "body", "b")));
        }
        assertEquals(0, countMessages());
    }

    //--------------------------------------------------------------

    @Test
    void zeroOrMultipleAdminsFailWithoutWritesOrReadChanges() {
        Message incoming = message(alice, admin, "s", "b", 0, false);
        admin.setRoles(new java.util.HashSet<>());
        new app.daos.UserDAO(em).update(admin);
        assertTrue(dao.getAdmins().isEmpty());
        assertConfigurationFailure();
        admin.getRoles().add(Role.ADMIN);
        new app.daos.UserDAO(em).update(admin);
        user("Second", "Admin", Role.ADMIN);
        assertConfigurationFailure();
        assertEquals(1, countMessages());
        assertFalse(reload(incoming.getId()).isRead());
    }

    //--------------------------------------------------------------

    @Test
    void validatesRequiredTextLengthsAndRejectsUnknownOrControlledFields() {
        for (String field : List.of("subject", "body")) {
            for (Object value : List.of("", " ", 5, true, List.of(), Map.of(), "x".repeat(field.equals("body") ? 5001 : 201))) {
                Map<String, Object> request = new HashMap<>(Map.of("subject", "s", "body", "b"));
                request.put(field, value);
                error(400, () -> service.sendCustomer(alice.getId(), request));
                error(400, () -> service.sendAdmin(admin.getId(), alice.getId(), request));
            }
            Map<String, Object> request = new HashMap<>(Map.of("subject", "s", "body", "b"));
            request.remove(field);
            error(400, () -> service.sendCustomer(alice.getId(), request));
            error(400, () -> service.sendAdmin(admin.getId(), alice.getId(), request));
            request.put(field, null);
            error(400, () -> service.sendCustomer(alice.getId(), request));
        }
        for (String field : List.of("id", "senderId", "recipientId", "createdAt", "read", "unknown")) {
            Map<String, Object> request = new HashMap<>(Map.of("subject", "s", "body", "b"));
            request.put(field, null);
            error(400, () -> service.sendCustomer(alice.getId(), request));
            error(400, () -> service.sendAdmin(admin.getId(), alice.getId(), request));
        }
        error(400, () -> service.sendCustomer(alice.getId(), null));
        assertEquals(0, countMessages());
        service.sendCustomer(alice.getId(), Map.of("subject", "s".repeat(200), "body", "b".repeat(5000)));
        error(400, () -> service.sendAdmin(admin.getId(), alice.getId(), Map.of("subject", "s", "body", "b")));
        error(400, () -> service.sendAdmin(admin.getId(), alice.getId(), Map.of("subject", "", "body", "b")));
        error(400, () -> service.sendAdmin(admin.getId(), alice.getId(), Map.of("body", " ")));
        assertEquals(1, countMessages());
    }

    //--------------------------------------------------------------

    private void assertConfigurationFailure() {
        error(500, () -> service.getMine(alice.getId()));
        error(500, () -> service.sendCustomer(alice.getId(), Map.of("subject", "s", "body", "b")));
        error(500, () -> service.getThreads(admin.getId(), null));
        error(500, () -> service.getThread(admin.getId(), alice.getId()));
        error(500, () -> service.sendAdmin(admin.getId(), alice.getId(), Map.of("body", "b")));
        error(500, () -> service.markRead(admin.getId(), alice.getId()));
    }

    //--------------------------------------------------------------

    private List<UUID> ids(String sort) {
        return service.getThreads(admin.getId(), sort).stream().map(ThreadDTO::getCustomerId).toList();
    }

    //--------------------------------------------------------------

    private void error(int status, Runnable operation) {
        assertEquals(status, assertThrows(ApiException.class, operation::run).getStatus());
    }
}
