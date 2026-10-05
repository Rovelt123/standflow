package app.services;

import app.entities.Application;
import app.enums.ApplicationStandType;
import app.enums.ApplicationStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleApplicationPdfGeneratorTest {

    @Test
    void generatesPdfBytesContainingApplicationDetails() {
        Application application = new Application();
        application.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        application.setCompany("Nordic Winter Craft ApS");
        application.setContact("Anna Hansen");
        application.setCvr("01234567");
        application.setEmail("kontakt@example.dk");
        application.setPhone("+45 88 88 88 88");
        application.setAddress("Bredgade 12");
        application.setCity("4900 Maribo");
        application.setWebsite("https://example.dk");
        application.setProducts("Handlavede sweatre");
        application.setPreviousExhibitor(false);
        application.setStandType(ApplicationStandType.B);
        application.setTables(2);
        application.setChairs(4);
        application.setStatus(ApplicationStatus.PENDING);
        application.setCreatedAt(LocalDate.of(2026, 10, 5));

        byte[] pdf = new SimpleApplicationPdfGenerator().generate(application);
        String content = new String(pdf, StandardCharsets.ISO_8859_1);

        assertTrue(content.startsWith("%PDF-1.4"));
        assertTrue(content.contains("/Type /Catalog"));
        assertTrue(content.contains("Nordic Winter Craft ApS"));
        assertTrue(content.contains("kontakt@example.dk"));
    }
}
