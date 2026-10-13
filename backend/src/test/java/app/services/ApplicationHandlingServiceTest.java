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
    void privateApplicantsCanCreateAndEditWithoutCompanyCvrOrWebsite() throws Exception {
        UUID userId = UUID.randomUUID();
        for (String empty : new String[]{null, "", "   "}) {
            var body = validBody();
            for (String field : new String[]{"company", "cvr", "website"}) {
                if (empty == null) body.remove(field);
                else body.put(field, empty);
            }
            Application saved = service.create(request(body), userId);
            em.clear();
            saved = service.getById(saved.getId());
            assertEquals("", saved.getCompany());
            assertEquals("", saved.getCvr());
            assertNull(saved.getWebsite());
            assertTrue(new SimpleApplicationPdfGenerator().generate(saved).length > 0);
            service.updateStatus(saved.getId(), Map.of("status", "INFO_REQUESTED"));
            Application updated = service.updateOwn(saved.getId(), userId, request(body));
            assertEquals("", updated.getCompany());
            assertEquals("", updated.getCvr());
            assertNull(updated.getWebsite());
            assertEquals(ApplicationStatus.PENDING, updated.getStatus());
        }
        assertEquals(3, emailService.calls());
    }

    //--------------------------------------------------------------

    @Test
    void optionalBusinessFieldsStillValidateProvidedValues() throws Exception {
        for (String cvr : new String[]{"1234", "123456789", "abcdefgh"}) {
            var input = request(validBody());
            input.setCvr(cvr);
            assertEquals(400, assertThrows(ApiException.class, () -> service.create(input)).getStatus());
        }
        var input = request(validBody());
        input.setCompany("x".repeat(201));
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(input)).getStatus());
        input.setCompany("");
        input.setWebsite("javascript:alert(1)");
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(input)).getStatus());
        assertEquals(0, countApplications());
        assertEquals(0, emailService.calls());
    }

    //--------------------------------------------------------------

    @Test
    void pdfFailureLeavesExactlyOneSavedApplicationAndDoesNotSend() throws Exception {
        pdfGenerator.failWith(new ApiException(500, "PDF generation failed"));
        var input = request(validBody());
        assertThrows(ApiException.class, () -> service.create(input));
        assertEquals(1, countApplications());
        assertEquals(1, pdfGenerator.calls());
        assertEquals(0, emailService.calls());
        assertEquals(ApplicationStatus.PENDING, service.getAll().getFirst().getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void deliveryFailureLeavesExactlyOneSavedApplicationWithoutRetrying() throws Exception {
        emailService.failWith(new ApiException(500, "SMTP delivery failed"));
        var input = request(validBody());
        assertThrows(ApiException.class, () -> service.create(input));
        assertEquals(1, countApplications());
        assertEquals(1, pdfGenerator.calls());
        assertEquals(1, emailService.calls());
        assertEquals(ApplicationStatus.PENDING, service.getAll().getFirst().getStatus());
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
