package app.services;

import app.entities.Application;
import app.enums.ApplicationStatus;
import app.exceptions.ApiException;
import app.support.ApplicationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationHistoryTest extends ApplicationTestSupport {

    @Test
    void onlyAcceptedApplicationsBelongingToUserAreReusable() throws Exception {
        UUID user = UUID.randomUUID();
        Application first = service.create(request(validBody()), user);
        assertTrue(service.getReusable(user).isEmpty());
        service.updateStatus(first.getId(), Map.of("status", "ACCEPTED"));
        assertEquals(1, service.getReusable(user).size());
        service.updateHistory(first.getId(), history(2021, true, "A12"));
        Application second = service.create(request(validBody().put("company", "Vinboden")), user);
        service.updateStatus(second.getId(), Map.of("status", "ACCEPTED"));
        service.updateHistory(second.getId(), history(2022, true, null));
        Application rejected = service.create(request(validBody()), user);
        service.updateStatus(rejected.getId(), Map.of("status", "REJECTED"));
        service.updateHistory(rejected.getId(), history(2023, false, null));
        assertEquals(java.util.List.of(second.getId(), first.getId()),
                service.getReusable(user).stream().map(Application::getId).toList());
        assertTrue(service.getReusable(UUID.randomUUID()).isEmpty());
        assertEquals(404, assertThrows(ApiException.class,
                () -> service.getOwnById(first.getId(), UUID.randomUUID())).getStatus());
        assertEquals(2, service.getHistory(rejected).size());
        assertEquals("A12", service.getHistory(rejected).get(1).getStandLocation());
    }

    //--------------------------------------------------------------

    @Test
    void acceptedLegacyApplicationIsReusableWithoutInventingItsYear() throws Exception {
        UUID user = UUID.randomUUID();
        Application legacy = service.create(request(validBody()), user);
        service.updateHistory(legacy.getId(), history(null, null, null));
        service.updateStatus(legacy.getId(), Map.of("status", "ACCEPTED"));
        assertEquals(legacy.getId(), service.getReusable(user).getFirst().getId());
        assertNull(service.getReusable(user).getFirst().getMarketYear());
        service.updateStatus(legacy.getId(), Map.of("status", "REJECTED"));
        assertTrue(service.getReusable(user).isEmpty());
        service.updateHistory(legacy.getId(), history(2021, true, "A1"));
        assertTrue(service.getReusable(user).isEmpty());
    }

    //--------------------------------------------------------------

    @Test
    void marketYearIsIndependentOfCreationDateAndUnknownIsNotGuessed() throws Exception {
        Application application = service.create(request(validBody()));
        assertEquals(2026, application.getMarketYear());
        service.updateHistory(application.getId(), history(2021, true, "B7"));
        assertEquals(2026, application.getCreatedAt().getYear());
        assertEquals(1, service.getForYear("2021").size());
        assertTrue(service.getForYear(null).isEmpty());
        assertTrue(service.getForYear("2020").isEmpty());
        service.updateHistory(application.getId(), history(null, null, null));
        assertEquals(1, service.getForYear("unknown").size());
        assertThrows(ApiException.class, () -> service.getForYear("2021 OR 1=1"));
        assertThrows(ApiException.class, () -> service.getForYear("1899"));
    }

    //--------------------------------------------------------------

    @Test
    void historyValidationAndCorrectionPreserveOtherFields() throws Exception {
        Application application = service.create(request(validBody()));
        for (Map<String, Object> invalid : java.util.List.of(history(null, true, "A1"),
                history(2026, true, "A1"), history(2020, false, "A1"), history(2020, true, "x".repeat(201)))) {
            assertThrows(ApiException.class, () -> service.updateHistory(application.getId(), invalid));
        }
        assertNull(application.getAttended());
        service.updateHistory(application.getId(), history(2021, true, "A1"));
        service.updateHistory(application.getId(), history(2022, true, "B2"));
        try (var reader = emf.createEntityManager()) {
            Application saved = reader.find(Application.class, application.getId());
            assertEquals(2022, saved.getMarketYear());
            assertEquals("B2", saved.getStandLocation());
            assertEquals(ApplicationStatus.PENDING, saved.getStatus());
            assertEquals(application.getCreatedAt(), saved.getCreatedAt());
        }
        for (String field : java.util.List.of("marketYear", "attended", "standLocation")) {
            assertThrows(ApiException.class, () -> service.validateRequestFields(Map.of(field, "injected")));
        }
    }

    //--------------------------------------------------------------

    @Test
    void creatingFromHistoricalFieldsDoesNotCopyAdministrativeData() throws Exception {
        UUID user = UUID.randomUUID();
        Application previous = service.create(request(validBody()), user);
        service.updateHistory(previous.getId(), history(2021, true, "A1"));
        service.updateStatus(previous.getId(), Map.of("status", "ACCEPTED"));
        service.updateComment(previous.getId(), Map.of("comment", "Internal", "customerNote", "Note"));
        var copied = new app.mappers.ApplicationMapper().toDTO(previous);
        Application created = service.create(copied, user);
        assertNotEquals(previous.getId(), created.getId());
        assertEquals(2026, created.getMarketYear());
        assertEquals(ApplicationStatus.PENDING, created.getStatus());
        assertNull(created.getStandLocation());
        assertNull(created.getAttended());
        assertNull(created.getInternalComment());
        assertNull(created.getCustomerNote());
        assertEquals(2021, service.getById(previous.getId()).getMarketYear());
        assertEquals("A1", service.getById(previous.getId()).getStandLocation());
    }

    //--------------------------------------------------------------

    @Test
    void ownUpdatePreservesRegisteredMarketYearAndHistory() throws Exception {
        UUID user = UUID.randomUUID();
        Application application = service.create(request(validBody()), user);
        service.updateHistory(application.getId(), history(2021, true, "A1"));
        service.updateStatus(application.getId(), Map.of("status", "INFO_REQUESTED"));
        Application updated = service.updateOwn(application.getId(), user, request(validBody()));
        assertEquals(2021, updated.getMarketYear());
        assertEquals(true, updated.getAttended());
        assertEquals("A1", updated.getStandLocation());
    }

    //--------------------------------------------------------------

    @Test
    void standTypeMailIsSentOnceAfterSaveAndFailureIsExplicit() throws Exception {
        Application application = service.create(request(validBody()));
        assertThrows(ApiException.class, () -> service.updateStandType(application.getId(), Map.of("standType", "Z")));
        assertEquals(1, emailService.calls());
        var result = service.updateStandType(application.getId(), Map.of("standType", "A"));
        assertEquals("SENT", result.notificationStatus());
        assertEquals(2, emailService.calls());
        try (var reader = emf.createEntityManager()) {
            assertEquals("A", reader.find(Application.class, application.getId()).getStandType().name());
        }
        assertEquals("UNCHANGED", service.updateStandType(application.getId(), Map.of("standType", "A")).notificationStatus());
        assertEquals(2, emailService.calls());
        emailService.failWith(new ApiException(500, "SMTP failed"));
        assertEquals("FAILED", service.updateStandType(application.getId(), Map.of("standType", "C")).notificationStatus());
        assertEquals("C", service.getById(application.getId()).getStandType().name());
        assertEquals("UNCHANGED", service.updateStandType(application.getId(), Map.of("standType", "C")).notificationStatus());
        assertEquals(3, emailService.calls());
    }

    //--------------------------------------------------------------

    private Map<String, Object> history(Integer year, Boolean attended, String location) {
        Map<String, Object> body = new HashMap<>();
        body.put("marketYear", year);
        body.put("attended", attended);
        body.put("standLocation", location);
        return body;
    }

    //--------------------------------------------------------------

    @Test
    void mailObservesCommittedChangeAndIsNotCalledWhenSaveFails() throws Exception {
        Application application = service.create(request(validBody()));
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        ApplicationEmailService mail = new ApplicationEmailService() {
            public void sendApplication(Application value, byte[] pdf, String filename) { }

            //--------------------------------------------------------------

            public void sendStandTypeChange(Application value, String previous) {
                try (var reader = emf.createEntityManager()) {
                    assertEquals("A", reader.find(Application.class, value.getId()).getStandType().name());
                }
                assertEquals("B", previous);
                sends.incrementAndGet();
            }
        };
        var tested = new ApplicationService(dao, intake::get, clock, pdfGenerator, mail);
        tested.updateStandType(application.getId(), Map.of("standType", "A"));
        assertEquals(1, sends.get());
        var failingDao = new app.daos.ApplicationDAO(em) {
            @Override
            public StandTypeUpdate updateStandType(UUID id, app.enums.ApplicationStandType type) {
                throw new ApiException(500, "Save failed");
            }
        };
        var failing = new ApplicationService(failingDao, intake::get, clock, pdfGenerator, mail);
        assertThrows(ApiException.class, () -> failing.updateStandType(application.getId(), Map.of("standType", "C")));
        assertEquals(1, sends.get());
        assertEquals("A", service.getById(application.getId()).getStandType().name());
    }
}
