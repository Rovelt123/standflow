package app.dtos;

import lombok.*;

import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TemplateDTO {

    private UUID id;

}
