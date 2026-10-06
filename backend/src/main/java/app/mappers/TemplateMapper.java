package app.mappers;

import app.dtos.TemplateDTO;
import app.entities.Template;
import app.mappers.generic.IMapper;

public class TemplateMapper implements IMapper<Template, TemplateDTO> {

    @Override
    public Template toEntity(TemplateDTO dto) {
        return Template.builder()
            .id(dto.getId())
            .name(dto.getName())
            .subject(dto.getSubject())
            .body(dto.getBody())
            .build();
    }

    //--------------------------------------------------------------

    @Override
    public TemplateDTO toDTO(Template entity) {
        return TemplateDTO.builder()
            .id(entity.getId())
            .name(entity.getName())
            .subject(entity.getSubject())
            .body(entity.getBody())
            .build();
    }
}
