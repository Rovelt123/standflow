package app.dtos;

import lombok.*;

import java.util.UUID;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MessageDTO {
    private UUID id;
    private UUID senderId;
    private UUID recipientId;
    private String subject;
    private String body;
    private String createdAt;
    private boolean read;
    @Builder.Default
    private List<FileDTO> attachments = List.of();
}
