package app.mappers;

import app.dtos.ApplicationDTO;
import app.entities.Application;
import app.enums.ApplicationStandType;
import app.enums.ApplicationStatus;
import app.mappers.generic.IMapper;
import app.utils.ErrorHandler;

public class ApplicationMapper implements IMapper<Application, ApplicationDTO> {

    @Override
    public Application toEntity(ApplicationDTO dto) {
        return Application.builder()
                .id(dto.getId())
                .company(dto.getCompany())
                .contact(dto.getContact())
                .cvr(dto.getCvr())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .city(dto.getCity())
                .website(dto.getWebsite())
                .products(dto.getProducts())
                .previousExhibitor(dto.getPreviousExhibitor())
                .standType(ErrorHandler.tryParseEnum(ApplicationStandType.class, dto.getStandType(), "Ugyldig standtype."))
                .tables(dto.getTables())
                .chairs(dto.getChairs())
                .status(ErrorHandler.tryParseEnum(ApplicationStatus.class, dto.getStatus(), "Ugyldig status."))
                .createdAt(ErrorHandler.tryParseLocalDate(dto.getCreatedAt(), "Ugyldig oprettelsesdato."))
                .build();
    }

    //--------------------------------------------------------------

    @Override
    public ApplicationDTO toDTO(Application entity) {
        return ApplicationDTO.builder()
                .id(entity.getId())
                .company(entity.getCompany())
                .contact(entity.getContact())
                .cvr(entity.getCvr())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .city(entity.getCity())
                .website(entity.getWebsite())
                .products(entity.getProducts())
                .previousExhibitor(entity.isPreviousExhibitor())
                .standType(entity.getStandType().name())
                .tables(entity.getTables())
                .chairs(entity.getChairs())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt().toString())
                .customerNote(entity.getCustomerNote())
                .build();
    }
}
