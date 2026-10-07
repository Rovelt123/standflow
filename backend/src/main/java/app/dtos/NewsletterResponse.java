package app.dtos;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class NewsletterResponse {

    private int sentTo;
    private int skippedNoConsent;
    private int failedToSend;
}
