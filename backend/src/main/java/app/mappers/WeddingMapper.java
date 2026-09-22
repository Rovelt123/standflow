package app.mappers;

import app.dtos.TemplateDTO;
import app.entities.Template;
import app.mappers.generic.IMapper;

public class WeddingMapper implements IMapper<Template, TemplateDTO> {


    @Override
    public Template toEntity(TemplateDTO dto) {

        return Template.builder()
            .id(dto.getId())
            .build();
    }

    // ________________________________________________________

    @Override
    public TemplateDTO toDTO(Template entity) {
        return TemplateDTO.builder()
            .id(entity.getId())
            .build();
    }
}
