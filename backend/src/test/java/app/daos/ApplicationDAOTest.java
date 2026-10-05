package app.daos;

import app.entities.Application;
import app.enums.ApplicationStatus;
import app.exceptions.ApiException;
import app.mappers.ApplicationMapper;
import app.support.ApplicationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationDAOTest extends ApplicationTestSupport {

    @Test
    void commitsEveryFieldAndAllowsRepeatedContactDetails() throws Exception {
        var receipt = service.create(request(validBody()));
        try (var reader = emf.createEntityManager()) {
            Application saved = reader.find(Application.class, receipt.id());
            assertAll(
                    () -> assertEquals("Nordic Winter Craft ApS", saved.getCompany()),
                    () -> assertEquals("Anna Hansen", saved.getContact()),
                    () -> assertEquals("01234567", saved.getCvr()),
                    () -> assertEquals("kontakt@example.dk", saved.getEmail()),
                    () -> assertEquals("+45 88 88 88 88", saved.getPhone()),
                    () -> assertEquals("Bredgade 12", saved.getAddress()),
                    () -> assertEquals("4900 Maribo", saved.getCity()),
                    () -> assertEquals("https://www.example.dk", saved.getWebsite()),
                    () -> assertEquals("Håndlavede sweatre", saved.getProducts()),
                    () -> assertFalse(saved.isPreviousExhibitor()),
                    () -> assertEquals("B", saved.getStandType().name()),
                    () -> assertEquals(2, saved.getTables()),
                    () -> assertEquals(4, saved.getChairs()),
                    () -> assertEquals(ApplicationStatus.PENDING, saved.getStatus()),
                    () -> assertEquals(LocalDate.of(2026, 10, 5), saved.getCreatedAt()));
        }
        var repeated = service.create(request(validBody()));
        assertNotEquals(receipt.id(), repeated.id());
        assertEquals(2, countApplications());
    }

    //--------------------------------------------------------------

    @Test
    void rollsBackFailedInsertAndCanSaveNextApplication() throws Exception {
        Application invalid = new ApplicationMapper().toEntity(request(validBody()), LocalDate.now());
        invalid.setCompany("x".repeat(201));
        ApiException error = assertThrows(ApiException.class, () -> dao.create(invalid));
        assertEquals(500, error.getStatus());
        assertEquals("Ansøgningen kunne ikke gemmes.", error.getMessage());
        assertFalse(em.getTransaction().isActive());
        assertEquals(0, countApplications());
        service.create(request(validBody()));
        assertEquals(1, countApplications());
    }

    //--------------------------------------------------------------

    @Test
    void refusesReceiptInsideAnUncommittedOuterTransaction() throws Exception {
        var request = request(validBody());
        em.getTransaction().begin();
        assertEquals(500, assertThrows(ApiException.class, () -> service.create(request)).getStatus());
        assertEquals(0, countApplications());
        em.getTransaction().rollback();
    }
}
