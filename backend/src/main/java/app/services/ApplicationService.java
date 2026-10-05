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
        this.applicationDAO = Objects.requireNonNull(applicationDAO);
        this.intakeStatus = Objects.requireNonNull(intakeStatus);
        this.clock = Objects.requireNonNull(clock).withZone(ZoneOffset.UTC);
    }

    //--------------------------------------------------------------

    public ApplicationResponseDTO create(ApplicationRequestDTO request) {
        requireOpenIntake();
        ApplicationRequestDTO normalized = validate(request);
        Application application = mapper.toEntity(normalized, LocalDate.now(clock));
        try {
            return mapper.toDTO(applicationDAO.create(application));
        } catch (RuntimeException exception) {
            throw new ApiException(500, "Ansøgningen kunne ikke gemmes.");
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

    private ApplicationRequestDTO validate(ApplicationRequestDTO request) {
        if (request == null) {
            throw new ApiException(400, "Ansøgningen skal være et JSON-objekt.");
        }
        String company = requiredText(request.company(), "Virksomhedsnavn", 200);
        String contact = requiredText(request.contact(), "Kontaktperson", 150);
        String cvr = requiredText(request.cvr(), "CVR", 8);
        String email = requiredText(request.email(), "E-mailadresse", 254);
        String phone = requiredText(request.phone(), "Telefonnummer", 30);
        String address = requiredText(request.address(), "Adresse", 255);
        String city = requiredText(request.city(), "Postnr. og by", 150);
        String products = requiredText(request.products(), "Produktbeskrivelse", 5000);
        String standType = requiredText(request.standType(), "Standtype", 1);
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
        if (request.previousExhibitor() == null) {
            throw new ApiException(400, "Tidligere stadeholder skal angives som true eller false.");
        }
        validateQuantity(request.tables(), "Antal borde");
        validateQuantity(request.chairs(), "Antal stole");
        return new ApplicationRequestDTO(company, contact, cvr, email, phone, address, city,
                normalizeWebsite(request.website()), products, request.previousExhibitor(),
                standType, request.tables(), request.chairs());
    }

    //--------------------------------------------------------------

    private String requiredText(String value, String label, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, label + " skal udfyldes.");
        }
        String normalized = value.strip();
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
