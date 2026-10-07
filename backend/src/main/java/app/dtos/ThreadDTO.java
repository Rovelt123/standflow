package app.dtos;

import lombok.*;

import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ThreadDTO {
    private UUID customerId;
    private String customerName;
    private String company;
    private String subject;
    private String lastMessage;
    private String lastMessageAt;
    private boolean unread;
}
