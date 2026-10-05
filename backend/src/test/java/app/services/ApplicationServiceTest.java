package app.services;

import app.exceptions.ApiException;
import app.support.ApplicationTestSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationServiceTest extends ApplicationTestSupport {

    @Test
    void validatesRequiredTextAndLengthBoundaries() throws Exception {
        Map<String, String> maximumValues = Map.of(
                "company", "a".repeat(200), "contact", "a".repeat(150), "cvr", "01234567",
                "email", "a".repeat(242) + "@example.com", "phone", "12345678" + "-".repeat(22),
                "address", "a".repeat(255), "city", "4900 " + "a".repeat(145),
                "products", "a".repeat(5000), "standType", "H");
        for (var field : maximumValues.entrySet()) {
            ObjectNode body = validBody();
            body.remove(field.getKey());
            assertInvalid(body);
            body.putNull(field.getKey());
            assertInvalid(body);
            body.put(field.getKey(), " \t ");
            assertInvalid(body);
            body.put(field.getKey(), field.getValue() + "a");
            assertInvalid(body);
        }
        assertEquals(0, countApplications());
        ObjectNode maximum = validBody();
        maximumValues.forEach(maximum::put);
        assertNotNull(service.create(request(maximum)).getId());
    }

    //--------------------------------------------------------------

    @Test
    void rejectsInvalidFormatsAndQuantities() throws Exception {
        Map<String, String> invalid = Map.of("cvr", "abcdefgh", "email", "anna@@example.dk",
                "phone", "+45 abcdefgh", "city", "Maribo", "website", "javascript:alert(1)",
                "standType", "I");
        for (var field : invalid.entrySet()) {
            assertInvalid(validBody().put(field.getKey(), field.getValue()));
        }
        for (String field : new String[]{"tables", "chairs", "previousExhibitor"}) {
            ObjectNode body = validBody();
            body.remove(field);
            assertInvalid(body);
            body.putNull(field);
            assertInvalid(body);
        }
        for (String field : new String[]{"tables", "chairs"}) {
            assertInvalid(validBody().put(field, -1));
            assertInvalid(validBody().put(field, 101));
        }
        assertEquals(0, countApplications());
    }

    //--------------------------------------------------------------

    @Test
    void acceptsStandTypesQuantityBoundariesAndOptionalWebsite() throws Exception {
        for (String code : new String[]{"A", "B", "C", "D", "E", "F", "G", "H"}) {
            ObjectNode body = validBody().put("standType", code).put("tables", 0).put("chairs", 100);
            body.remove("website");
            var receipt = service.create(request(body));
            assertEquals(LocalDate.of(2026, 10, 5), receipt.getCreatedAt());
            assertNull(dao.getById(receipt.getId()).getWebsite());
        }
        var receipt = service.create(request(validBody().put("website", "  ")));
        assertNull(dao.getById(receipt.getId()).getWebsite());
        assertEquals(9, countApplications());
    }

    //--------------------------------------------------------------

    @Test
    void sendsGeneratedPdfAfterValidApplicationIsSaved() throws Exception {
        var receipt = service.create(request(validBody()));

        assertNotNull(receipt.getId());
        assertEquals(1, pdfGenerator.calls());
        assertEquals(1, emailService.calls());
        assertArrayEquals("%PDF-1.4 test".getBytes(), emailService.lastAttachment());
        assertEquals("application-" + receipt.getId() + ".pdf", emailService.lastFilename());
    }

    //--------------------------------------------------------------

    @Test
    void doesNotGeneratePdfOrSendEmailWhenValidationFails() throws Exception {
        assertInvalid(validBody().put("email", "invalid"));

        assertEquals(0, pdfGenerator.calls());
        assertEquals(0, emailService.calls());
    }

    //--------------------------------------------------------------

    @Test
    void doesNotSendEmailWhenPdfGenerationFails() throws Exception {
        pdfGenerator.failWith(new ApiException(500, "pdf failed"));

        ApiException error = assertThrows(ApiException.class, () -> service.create(request(validBody())));

        assertEquals(500, error.getStatus());
        assertEquals(1, pdfGenerator.calls());
        assertEquals(0, emailService.calls());
    }

    //--------------------------------------------------------------

    @Test
    void handlesEmailFailureWithApiException() throws Exception {
        emailService.failWith(new ApiException(500, "email failed"));

        ApiException error = assertThrows(ApiException.class, () -> service.create(request(validBody())));

        assertEquals(500, error.getStatus());
        assertEquals(1, pdfGenerator.calls());
        assertEquals(1, emailService.calls());
    }

    //--------------------------------------------------------------

    @Test
    void validatesPhoneEmailCityAndWebsiteVariants() throws Exception {
        for (String phone : new String[]{"1234567", "1234567890123456", "12+345678", "1234/5678"}) {
            assertInvalid(validBody().put("phone", phone));
        }
        for (String email : new String[]{"anna", "anna@", "@example.dk", "anna @example.dk", "anna@example..dk"}) {
            assertInvalid(validBody().put("email", email));
        }
        for (String city : new String[]{"490 Maribo", "49000 Maribo", "4900", "4900   "}) {
            assertInvalid(validBody().put("city", city));
        }
        for (String website : new String[]{"ftp://example.dk", "https://", "https://user:pass@example.dk",
                "https://example.dk:99999", "https://example.dk/a b", "https://example.dk/" + "x".repeat(238)}) {
            assertInvalid(validBody().put("website", website));
        }
        assertEquals(0, countApplications());
        String maximumWebsite = "https://example.dk/" + "x".repeat(236);
        var receipt = service.create(request(validBody().put("website", maximumWebsite)));
        assertEquals(maximumWebsite, dao.getById(receipt.getId()).getWebsite());
        var noWebsite = service.create(request(validBody().putNull("website")));
        assertNull(dao.getById(noWebsite.getId()).getWebsite());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(null)).getStatus());
    }

    //--------------------------------------------------------------

    @Test
    void observesManualStatusChangesWithoutAutomaticallyOpeningOnDate() throws Exception {
        var request = request(validBody());
        service.create(request);
        intake.set(new ApplicationService.IntakeStatus(false, LocalDate.of(2026, 1, 1)));
        ApiException closed = assertThrows(ApiException.class, () -> service.create(request));
        assertEquals(409, closed.getStatus());
        assertTrue(closed.getMessage().contains("2026-01-01"));
        intake.set(new ApplicationService.IntakeStatus(false, null));
        assertTrue(assertThrows(ApiException.class, () -> service.create(request))
                .getMessage().contains("endnu ikke fastlagt"));
        assertEquals(1, countApplications());
        intake.set(new ApplicationService.IntakeStatus(true, LocalDate.of(2027, 1, 1)));
        service.create(request);
        assertEquals(2, countApplications());
    }

    //--------------------------------------------------------------

    @Test
    void unavailableStatusFailsClosedWithoutLeakingDetails() throws Exception {
        var request = request(validBody());
        intake.set(null);
        assertEquals(503, assertThrows(ApiException.class, () -> service.create(request)).getStatus());
        var unavailable = new ApplicationService(dao, () -> {
            throw new IllegalStateException("private database details");
        });
        ApiException error = assertThrows(ApiException.class, () -> unavailable.create(request));
        assertEquals(503, error.getStatus());
        assertFalse(error.getMessage().contains("private"));
        assertEquals(0, countApplications());
    }

    //--------------------------------------------------------------

    private void assertInvalid(ObjectNode body) throws Exception {
        var request = request(body);
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(request)).getStatus());
    }
}
