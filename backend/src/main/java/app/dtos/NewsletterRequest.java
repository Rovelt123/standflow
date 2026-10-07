package app.dtos;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class NewsletterRequest {

    private String audience;
    private UUID templateId;
    private List<UUID> recipientIds;
    private String category;
}
