package app.services;

import app.dtos.TemplateDTO;
import app.entities.Template;
import app.exceptions.ApiException;
import app.support.TemplateTestSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    // Nye tests: slet, variabler, render og validering
    //--------------------------------------------------------------

    @Test
    void createThenDeleteRemovesTemplate() throws Exception {
        UUID id = service.create(request(validBody())).getId();
        assertEquals(1, countTemplates());
        service.delete(id);
        assertEquals(0, countTemplates());
        assertEquals(404, assertThrows(ApiException.class, () -> service.getById(id)).getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void deletingUnknownTemplateGives404() {
        assertEquals(404, assertThrows(ApiException.class, () -> service.delete(UUID.randomUUID())).getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void renderFillsKnownVariables() throws Exception {
        UUID id = service.create(request(validBody()
                .put("subject", "Tak <Firstname>")
                .put("body", "Hej <Firstname> <Lastname> fra <Company>. Skriv til <Email>."))).getId();

        TemplateService.RenderedTemplate rendered = service.render(id, Map.of(
                "Firstname", "Anna",
                "Lastname", "Hansen",
                "Company", "Nordic Craft",
                "Email", "anna@nordic.dk"
        ));

        assertEquals("Tak Anna", rendered.subject());
        assertEquals("Hej Anna Hansen fra Nordic Craft. Skriv til anna@nordic.dk.", rendered.body());
        assertTrue(rendered.unknownVariables().isEmpty());
    }

    //--------------------------------------------------------------

    @Test
    void unknownVariableIsLeftIntactAndReported() throws Exception {
        UUID id = service.create(request(validBody()
                .put("subject", "Hej <Fornvan>")
                .put("body", "Velkommen <Firstname>"))).getId();

        TemplateService.RenderedTemplate rendered = service.render(id, Map.of("Firstname", "Anna"));

        assertEquals("Hej <Fornvan>", rendered.subject());
        assertEquals("Velkommen Anna", rendered.body());
        assertEquals(List.of("<Fornvan>"), rendered.unknownVariables());
    }

    //--------------------------------------------------------------

    @Test
    void unknownVariablesDetectsTypoInDraftWithoutSaving() {
        assertTrue(service.unknownVariables("Hej <Firstname> og <Company>").isEmpty());
        assertEquals(List.of("<Fornvan>"), service.unknownVariables("Hej <Fornvan>"));
        assertEquals(0, countTemplates());
    }

    //--------------------------------------------------------------

    @Test
    void renderDoesNotChangeStoredTemplate() throws Exception {
        UUID id = service.create(request(validBody().put("body", "Hej <Firstname>"))).getId();

        service.render(id, Map.of("Firstname", "Anna"));

        assertEquals("Hej <Firstname>", reload(id).getBody());
    }

    //--------------------------------------------------------------

    @Test
    void missingValueForKnownVariableBecomesEmpty() throws Exception {
        UUID id = service.create(request(validBody()
                .put("body", "Hej <Firstname> <Lastname>"))).getId();

        TemplateService.RenderedTemplate rendered = service.render(id, Map.of("Firstname", "Anna"));

        assertEquals("Hej Anna ", rendered.body());
        assertTrue(rendered.unknownVariables().isEmpty());
    }

    //--------------------------------------------------------------

    private void assertInvalid(ObjectNode body) throws Exception {
        var request = request(body);
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(request)).getStatus());
    }
}
