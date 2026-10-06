package app.services;

import app.dtos.TemplateDTO;
import app.entities.Template;
import app.exceptions.ApiException;
import app.support.TemplateTestSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemplateServiceTest extends TemplateTestSupport {

    private static final Map<String, Integer> MAXIMUM_LENGTHS = Map.of("name", 150, "subject", 200, "body", 5000);

    @Test
    void validatesRequiredTextAndLengthBoundaries() throws Exception {
        for (var field : MAXIMUM_LENGTHS.entrySet()) {
            ObjectNode body = validBody();
            body.remove(field.getKey());
            assertInvalid(body);
            body.putNull(field.getKey());
            assertInvalid(body);
            body.put(field.getKey(), "");
            assertInvalid(body);
            body.put(field.getKey(), " \t\n ");
            assertInvalid(body);
            body.put(field.getKey(), "a".repeat(field.getValue() + 1));
            assertInvalid(body);
        }
        assertEquals(0, countTemplates());
        ObjectNode maximum = validBody();
        MAXIMUM_LENGTHS.forEach((field, length) -> maximum.put(field, "a".repeat(length)));
        assertNotNull(service.create(request(maximum)).getId());
        ObjectNode minimum = validBody();
        MAXIMUM_LENGTHS.keySet().forEach(field -> minimum.put(field, "a"));
        assertNotNull(service.create(request(minimum)).getId());
        assertEquals(2, countTemplates());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(null)).getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void ignoresClientIdAndStripsNameAndSubjectButKeepsBodyRaw() throws Exception {
        UUID clientId = UUID.randomUUID();
        TemplateDTO request = request(validBody().put("body", "  Hej <navn>,\n"));
        request.setId(clientId);
        TemplateDTO saved = service.create(request);
        assertNotNull(saved.getId());
        assertNotEquals(clientId, saved.getId());
        assertEquals("Kvittering", saved.getName());
        assertEquals("Tak for din ansøgning", saved.getSubject());
        assertEquals("  Hej <navn>,\n", saved.getBody());
    }

    //--------------------------------------------------------------

    @Test
    void updatesExistingTemplateAndKeepsPathId() throws Exception {
        UUID id = service.create(request(validBody())).getId();
        TemplateDTO request = request(validBody().put("name", "Ny navn").put("subject", "Nyt emne")
                .put("body", "Ny tekst til <navn>"));
        request.setId(UUID.randomUUID());
        TemplateDTO updated = service.update(id, request);
        assertEquals(id, updated.getId());
        Template saved = reload(id);
        assertEquals("Ny navn", saved.getName());
        assertEquals("Nyt emne", saved.getSubject());
        assertEquals("Ny tekst til <navn>", saved.getBody());
        assertEquals(1, countTemplates());
    }

    //--------------------------------------------------------------

    @Test
    void rejectsInvalidUpdateWithoutChangingData() throws Exception {
        UUID id = service.create(request(validBody())).getId();
        for (var field : MAXIMUM_LENGTHS.entrySet()) {
            ObjectNode blank = validBody().put(field.getKey(), " ");
            assertEquals(400, assertThrows(ApiException.class, () -> service.update(id, request(blank))).getStatus());
            ObjectNode tooLong = validBody().put(field.getKey(), "a".repeat(field.getValue() + 1));
            assertEquals(400, assertThrows(ApiException.class, () -> service.update(id, request(tooLong))).getStatus());
        }
        Template saved = reload(id);
        assertEquals("Kvittering", saved.getName());
        assertEquals("Tak for din ansøgning", saved.getSubject());
        assertEquals("Kære <navn>,\n\nTak for din ansøgning.", saved.getBody());
    }

    //--------------------------------------------------------------

    @Test
    void unknownIdReturns404ForGetAndUpdateWithoutCreatingRow() throws Exception {
        UUID unknown = UUID.randomUUID();
        assertEquals(404, assertThrows(ApiException.class, () -> service.getById(unknown)).getStatus());
        var request = request(validBody());
        assertEquals(404, assertThrows(ApiException.class, () -> service.update(unknown, request)).getStatus());
        assertEquals(0, countTemplates());
    }

    //--------------------------------------------------------------

    @Test
    void listsAllTemplates() throws Exception {
        assertTrue(service.getAll().isEmpty());
        service.create(request(validBody()));
        service.create(request(validBody().put("name", "Nyhedsbrev")));
        assertEquals(2, service.getAll().size());
    }

    //--------------------------------------------------------------

    private void assertInvalid(ObjectNode body) throws Exception {
        var request = request(body);
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(request)).getStatus());
    }
}
