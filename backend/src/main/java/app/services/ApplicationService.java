package app.services;

import app.daos.ApplicationDAO;
import app.dtos.ApplicationRequestDTO;
import app.dtos.ApplicationResponseDTO;
import app.entities.Application;
import app.exceptions.ApiException;
import app.mappers.ApplicationMapper;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Supplier;

public class ApplicationService {

    private final ApplicationDAO applicationDAO;
    private final Supplier<IntakeStatus> intakeStatus;
    private final Clock clock;
    private final ApplicationPdfGenerator pdfGenerator;
    private final ApplicationEmailService emailService;
    private final ApplicationMapper mapper = new ApplicationMapper();

    /** Supplied by the separate administration feature; dates never open intake automatically. */
    public record IntakeStatus(boolean open, LocalDate nextOpeningDate) {
    }

    //--------------------------------------------------------------

    public ApplicationService(ApplicationDAO applicationDAO, Supplier<IntakeStatus> intakeStatus) {
        this(applicationDAO, intakeStatus, Clock.systemUTC());
    }

    //--------------------------------------------------------------

    public ApplicationService(ApplicationDAO applicationDAO, Supplier<IntakeStatus> intakeStatus, Clock clock) {
        this(applicationDAO, intakeStatus, clock, new SimpleApplicationPdfGenerator(),
                new GmailSmtpApplicationEmailService());
    }

    //--------------------------------------------------------------

    public ApplicationService(ApplicationDAO applicationDAO, Supplier<IntakeStatus> intakeStatus, Clock clock,
                              ApplicationPdfGenerator pdfGenerator, ApplicationEmailService emailService) {
        this.applicationDAO = Objects.requireNonNull(applicationDAO);
        this.intakeStatus = Objects.requireNonNull(intakeStatus);
        this.clock = Objects.requireNonNull(clock).withZone(ZoneOffset.UTC);
        this.pdfGenerator = Objects.requireNonNull(pdfGenerator);
        this.emailService = Objects.requireNonNull(emailService);
    }

    //--------------------------------------------------------------

    public Application create(ApplicationDTO request) {
        requireOpenIntake();
        ApplicationRequestDTO normalized = validate(request);
        Application application = mapper.toEntity(normalized, LocalDate.now(clock));
        Application saved;
        ApplicationDTO normalized = validate(request);
        normalized.setStatus("PENDING");
        normalized.setCreatedAt(LocalDate.now(clock).toString());
        Application application = mapper.toEntity(normalized);
        try {
            saved = applicationDAO.create(application);
            return applicationDAO.createApplication(application);
        } catch (RuntimeException exception) {
            throw new ApiException(500, "Ansøgningen kunne ikke gemmes.");
        }
        byte[] pdf = pdfGenerator.generate(saved);
        emailService.sendApplication(saved, pdf, pdfGenerator.filename(saved));
        return mapper.toDTO(saved);
    }

    //--------------------------------------------------------------

    public void validateRequestFields(Map<String, ?> body) {
        if (body == null) {
            throw new ApiException(400, "Ansøgningen skal være et JSON-objekt.");
        }
        for (var field : body.entrySet()) {
            String name = field.getKey();
            Object value = field.getValue();
            boolean valid;
            if (TEXT_FIELDS.contains(name)) {
                valid = value == null || value instanceof String;
            } else if ("previousExhibitor".equals(name)) {
                valid = value == null || value instanceof Boolean;
            } else if ("tables".equals(name) || "chairs".equals(name)) {
                valid = value == null || value instanceof Integer;
            } else {
                throw new ApiException(400, "Ansøgningen indeholder ukendte felter.");
            }
            if (!valid) {
                throw new ApiException(400, "Ansøgningen indeholder en forkert felttype.");
            }
        }
    }

    //--------------------------------------------------------------

    private void requireOpenIntake() {
        IntakeStatus status;
        try {
            status = intakeStatus.get();
        } catch (RuntimeException exception) {
            throw new ApiException(503, "Ansøgningsstatus kunne ikke hentes. Prøv igen senere.");
        }
        if (status == null) {
            throw new ApiException(503, "Ansøgningsstatus kunne ikke hentes. Prøv igen senere.");
        }
        if (!status.open()) {
            String nextOpening = status.nextOpeningDate() == null
                    ? "Næste åbningsdato er endnu ikke fastlagt."
                    : "Der åbnes for ansøgninger igen " + status.nextOpeningDate() + ".";
            throw new ApiException(409, "Ansøgninger er lukket. " + nextOpening);
        }
    }

    //--------------------------------------------------------------

    private ApplicationDTO validate(ApplicationDTO request) {
        if (request == null) {
            throw new ApiException(400, "Ansøgningen skal være et JSON-objekt.");
        }
        String company = requiredText(request.getCompany(), "Virksomhedsnavn", 200);
        String contact = requiredText(request.getContact(), "Kontaktperson", 150);
        String cvr = requiredText(request.getCvr(), "CVR", 8);
        String email = requiredText(request.getEmail(), "E-mailadresse", 254);
        String phone = requiredText(request.getPhone(), "Telefonnummer", 30);
        String address = requiredText(request.getAddress(), "Adresse", 255);
        String city = requiredText(request.getCity(), "Postnr. og by", 150);
        String products = requiredText(request.getProducts(), "Produktbeskrivelse", 5000);
        String standType = requiredText(request.getStandType(), "Standtype", 1);
        if (!cvr.matches("[0-9]{8}")) {
            throw new ApiException(400, "CVR skal bestå af præcis 8 cifre.");
        }
        if (!email.matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")) {
            throw new ApiException(400, "E-mailadressen er ugyldig.");
        }
        int digits = phone.replaceAll("[^0-9]", "").length();
        if (!phone.matches("\\+?[0-9 ()-]+") || digits < 8 || digits > 15) {
            throw new ApiException(400, "Telefonnummeret skal indeholde 8–15 cifre.");
        }
        if (!city.matches("[0-9]{4} +\\S.*")) {
            throw new ApiException(400, "Angiv firecifret postnummer og by.");
        }
        if (!standType.matches("[A-H]")) {
            throw new ApiException(400, "Standtype skal være A–H.");
        }
        if (request.getPreviousExhibitor() == null) {
            throw new ApiException(400, "Tidligere stadeholder skal angives som true eller false.");
        }
        validateQuantity(request.getTables(), "Antal borde");
        validateQuantity(request.getChairs(), "Antal stole");
        return ApplicationDTO.builder()
                .company(company).contact(contact).cvr(cvr).email(email).phone(phone)
                .address(address).city(city).website(normalizeWebsite(request.getWebsite()))
                .products(products).previousExhibitor(request.getPreviousExhibitor()).standType(standType)
                .tables(request.getTables()).chairs(request.getChairs()).build();
    }

    //--------------------------------------------------------------

    private String requiredText(String value, String label, int maximumLength) {
        String normalized = ErrorHandler.tryString(value, label + " skal udfyldes.").strip();
        if (normalized.length() > maximumLength) {
            throw new ApiException(400, label + " må højst være " + maximumLength + " tegn.");
        }
        return normalized;
    }

    //--------------------------------------------------------------

    private void validateQuantity(Integer value, String label) {
        if (value == null || value < 0 || value > 100) {
            throw new ApiException(400, label + " skal være et heltal mellem 0 og 100.");
        }
    }

    //--------------------------------------------------------------

    private String normalizeWebsite(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (!normalized.contains("://")) {
            normalized = "https://" + normalized;
        }
        try {
            URI uri = URI.create(normalized);
            if (normalized.length() > 255 || uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(400, "Website skal være et gyldigt http/https-websted på højst 255 tegn.");
        }
        return normalized;
    }
}
