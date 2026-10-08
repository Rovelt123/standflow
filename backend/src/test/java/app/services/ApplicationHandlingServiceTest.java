package app.services;

import app.entities.Application;
import app.enums.ApplicationStatus;
import app.exceptions.ApiException;
import app.support.ApplicationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationHandlingServiceTest extends ApplicationTestSupport {

    @Test
    void customerNotePersistsAndLegacyCommentUpdatesPreserveIt() throws Exception {
        Application application = service.create(request(validBody()));
        service.updateComment(application.getId(), Map.of("comment", "Kun for Lise", "customerNote", "Oplys venligst CVR"));
        service.updateComment(application.getId(), Map.of("comment", "Ny intern kommentar"));
        try (var reader = emf.createEntityManager()) {
            Application saved = reader.find(Application.class, application.getId());
            assertEquals("Oplys venligst CVR", saved.getCustomerNote());
            assertEquals("Ny intern kommentar", saved.getInternalComment());
            assertEquals("Oplys venligst CVR", reader.createNativeQuery(
                    "select customer_note from applications where id = :id", String.class)
                    .setParameter("id", application.getId()).getSingleResult());
            var response = json.valueToTree(new app.mappers.ApplicationMapper().toDTO(saved));
            assertEquals("Oplys venligst CVR", response.path("customerNote").asText());
            assertFalse(response.has("comment"));
            assertFalse(response.has("internalComment"));
        }
        service.updateComment(application.getId(), Map.of("comment", "", "customerNote", ""));
        assertEquals("", service.getById(application.getId()).getCustomerNote());
        assertEquals(1, emailService.calls());
    }

    //--------------------------------------------------------------

    @Test
    void customerResubmissionPreservesAdminNoteAndRejectsPublicNoteInput() throws Exception {
        UUID customerId = UUID.randomUUID();
        Application application = service.create(request(validBody()), customerId);
        service.updateComment(application.getId(), Map.of("comment", "Private", "customerNote", "Ret dit CVR"));
        service.updateStatus(application.getId(), Map.of("status", "INFO_REQUESTED"));
        var request = request(validBody());
        request.setCustomerNote("Forged customer note");
        service.updateOwn(application.getId(), customerId, request);
        try (var reader = emf.createEntityManager()) {
            Application saved = reader.find(Application.class, application.getId());
            assertEquals("Ret dit CVR", saved.getCustomerNote());
            assertEquals("Private", saved.getInternalComment());
            assertEquals(ApplicationStatus.PENDING, saved.getStatus());
        }
        assertEquals(400, assertThrows(ApiException.class, () ->
                service.validateRequestFields(Map.of("customerNote", "Forged"))).getStatus());
        var parsed = json.readValue("{\"customerNote\":\"Forged\"}", app.dtos.ApplicationDTO.class);
        assertNull(parsed.getCustomerNote());
    }

    //--------------------------------------------------------------

    @Test
    void invalidNotesRejectEntireUpdateBeforeEitherFieldChanges() throws Exception {
        Application application = service.create(request(validBody()));
        service.updateComment(application.getId(), Map.of("comment", "Private", "customerNote", "Original"));
        for (Object value : new Object[]{123, true, null, "x".repeat(5001)}) {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("comment", "Changed");
            body.put("customerNote", value);
            assertEquals(400, assertThrows(ApiException.class, () -> service.updateComment(application.getId(), body)).getStatus());
        }
        assertEquals(400, assertThrows(ApiException.class, () -> service.updateComment(application.getId(),
                Map.of("comment", "Changed", "customerNote", "Note", "unknown", "x"))).getStatus());
        assertEquals(400, assertThrows(ApiException.class, () -> service.updateComment(application.getId(),
                Map.of("comment", "x".repeat(5001), "customerNote", "Changed"))).getStatus());
        try (var reader = emf.createEntityManager()) {
            Application saved = reader.find(Application.class, application.getId());
            assertEquals("Private", saved.getInternalComment());
            assertEquals("Original", saved.getCustomerNote());
        }
        service.updateComment(application.getId(), Map.of("comment", "Private", "customerNote", "x".repeat(5000)));
        assertEquals(5000, service.getById(application.getId()).getCustomerNote().length());
    }

    //--------------------------------------------------------------

    @Test
    void statusAndCommentsPersistIndependentlyWithoutSendingMail() throws Exception {
        assertTrue(service.getAll().isEmpty());
        Application application = service.create(request(validBody()));
        assertEquals(ApplicationStatus.PENDING, application.getStatus());
        service.updateComment(application.getId(), Map.of("comment", "Mangler CVR"));
        for (String status : new String[]{"ACCEPTED", "REJECTED", "INFO_REQUESTED", "ACCEPTED"}) {
            service.updateStatus(application.getId(), Map.of("status", status));
            try (var reader = emf.createEntityManager()) {
                Application saved = reader.find(Application.class, application.getId());
                assertEquals(status, saved.getStatus().name());
                assertEquals("Mangler CVR", saved.getInternalComment());
            }
        }
        service.updateComment(application.getId(), Map.of("comment", "CVR modtaget"));
        service.updateComment(application.getId(), Map.of("comment", ""));
        try (var reader = emf.createEntityManager()) {
            Application saved = reader.find(Application.class, application.getId());
            assertEquals("", saved.getInternalComment());
            assertEquals(ApplicationStatus.ACCEPTED, saved.getStatus());
        }
        assertEquals(1, emailService.calls());
        assertEquals(1, pdfGenerator.calls());
        assertEquals(1, service.getAll().size());
    }

    //--------------------------------------------------------------

    @Test
    void invalidChangesDoNotModifyTheApplication() throws Exception {
        Application application = service.create(request(validBody()));
        for (String status : new String[]{"PENDING", "UNKNOWN", "accepted", ""}) {
            assertThrows(ApiException.class, () -> service.updateStatus(application.getId(), Map.of("status", status)));
        }
        assertThrows(ApiException.class, () -> service.updateComment(application.getId(), Map.of("comment", "x".repeat(5001))));
        assertThrows(ApiException.class, () -> service.updateComment(application.getId(), Map.of("comment", 123)));
        assertThrows(ApiException.class, () -> service.updateStatus(application.getId(), Map.of("status", "ACCEPTED", "comment", "injected")));
        assertEquals(ApplicationStatus.PENDING, service.getById(application.getId()).getStatus());
        assertNull(service.getById(application.getId()).getInternalComment());
        assertThrows(ApiException.class, () -> service.getById(UUID.randomUUID()));
    }

    //--------------------------------------------------------------

    @Test
    void internalCommentIsExcludedFromEntitySerializationAndPublicInput() throws Exception {
        Application application = service.create(request(validBody()));
        service.updateComment(application.getId(), Map.of("comment", "Kun for Lise"));
        Application serializationSample = Application.builder().internalComment(application.getInternalComment()).build();
        String serialized = json.writeValueAsString(serializationSample);
        assertFalse(serialized.contains("Kun for Lise"));
        assertFalse(serialized.contains("internalComment"));
        String pdf = new String(new SimpleApplicationPdfGenerator().generate(application), StandardCharsets.ISO_8859_1);
        assertFalse(pdf.contains("Kun for Lise"));
        for (String field : new String[]{"comment", "internalComment", "status"}) {
            assertThrows(ApiException.class, () -> service.validateRequestFields(Map.of(field, "injected")));
        }
    }
}
