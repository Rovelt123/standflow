package app.mappers;

import app.dtos.ApplicationRequestDTO;
import app.dtos.ApplicationResponseDTO;
import app.entities.Application;
import app.enums.ApplicationStandType;
import app.enums.ApplicationStatus;

import java.time.LocalDate;

public class ApplicationMapper {

    public Application toEntity(ApplicationRequestDTO request, LocalDate createdAt) {
        Application application = new Application();
        application.setCompany(request.company());
        application.setContact(request.contact());
        application.setCvr(request.cvr());
        application.setEmail(request.email());
        application.setPhone(request.phone());
        application.setAddress(request.address());
        application.setCity(request.city());
        application.setWebsite(request.website());
        application.setProducts(request.products());
        application.setPreviousExhibitor(request.previousExhibitor());
        application.setStandType(ApplicationStandType.valueOf(request.standType()));
        application.setTables(request.tables());
        application.setChairs(request.chairs());
        application.setStatus(ApplicationStatus.PENDING);
        application.setCreatedAt(createdAt);
        return application;
    }

    //--------------------------------------------------------------

    public ApplicationResponseDTO toDTO(Application application) {
        return new ApplicationResponseDTO(application.getId(), application.getStatus(),
                application.getCreatedAt().toString());
    }
}
