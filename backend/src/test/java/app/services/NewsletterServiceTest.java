package app.services;

import app.dtos.NewsletterRequest;
import app.dtos.NewsletterResponse;
import app.entities.UnsubscribeToken;
import app.exceptions.ApiException;
import app.support.NewsletterTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NewsletterServiceTest extends NewsletterTestSupport {

    @Test
    void individualAudienceDeduplicatesRecipientsAndFiltersConsent() {
        NewsletterResponse response = newsletterService.send(NewsletterRequest.builder()
                .audience("INDIVIDUAL")
                .templateId(template.getId())
                .recipientIds(List.of(alice.getId(), alice.getId(), bob.getId()))
                .build());

        assertCounts(response, 1, 1, 0);
        assertEquals(List.of(alice.getId()), sentIds());
    }

    //--------------------------------------------------------------

    @Test
    void categoryAudienceUsesApplicationStandTypeAndDeduplicatesApplications() {
        NewsletterResponse response = newsletterService.send(NewsletterRequest.builder()
                .audience("CATEGORY")
                .templateId(template.getId())
                .category("A")
                .build());

        assertCounts(response, 1, 1, 0);
        assertEquals(List.of(alice.getId()), sentIds());
    }

    //--------------------------------------------------------------

    @Test
    void newStallholdersUsePreviousExhibitorFalse() {
        NewsletterResponse response = newsletterService.send(NewsletterRequest.builder()
                .audience("NEW_STALLHOLDERS")
                .templateId(template.getId())
                .build());

        assertCounts(response, 1, 1, 0);
        assertEquals(List.of(alice.getId()), sentIds());
    }

    //--------------------------------------------------------------

    @Test
    void previousYearStallholdersUsePreviousCalendarYearCreationDate() {
        NewsletterResponse response = newsletterService.send(NewsletterRequest.builder()
                .audience("PREVIOUS_YEAR_STALLHOLDERS")
                .templateId(template.getId())
                .build());

        assertCounts(response, 1, 0, 0);
        assertEquals(List.of(charlie.getId()), sentIds());
    }

    //--------------------------------------------------------------

    @Test
    void allPreviousStallholdersUsePreviousExhibitorTrue() {
        NewsletterResponse response = newsletterService.send(NewsletterRequest.builder()
                .audience("ALL_PREVIOUS_STALLHOLDERS")
                .templateId(template.getId())
                .build());

        assertCounts(response, 2, 0, 0);
        assertEquals(List.of(charlie.getId(), dave.getId()), sentIds());
    }

    //--------------------------------------------------------------

    @Test
    void allApplicantsUsesAllApplicationUsersWithoutDuplicates() {
        NewsletterResponse response = newsletterService.send(NewsletterRequest.builder()
                .audience("ALL_APPLICANTS")
                .templateId(template.getId())
                .build());

        assertCounts(response, 3, 1, 0);
        assertEquals(List.of(alice.getId(), charlie.getId(), dave.getId()), sentIds());
    }

    //--------------------------------------------------------------

    @Test
    void rendersTemplateSeparatelyForEachRecipientAndAddsUnsubscribeLink() {
        newsletterService.send(NewsletterRequest.builder()
                .audience("ALL_PREVIOUS_STALLHOLDERS")
                .templateId(template.getId())
                .build());

        SentNewsletter charlieMail = emailService.sent().get(0);
        SentNewsletter daveMail = emailService.sent().get(1);

        assertEquals("Hej Charlie Christensen", charlieMail.subject());
        assertEquals("Hej Dave Dahl", daveMail.subject());
        assertTrue(charlieMail.body().contains("Firma: Charlie Ceramics"));
        assertTrue(charlieMail.body().contains("Email: " + charlie.getEmail()));
        assertFalse(charlieMail.body().contains("Dave Design"));
        assertTrue(daveMail.body().contains("Firma: Dave Design"));
        assertFalse(daveMail.body().contains("Charlie Ceramics"));
        assertTrue(charlieMail.body().contains("/unsubscribe?token="));
        assertTrue(daveMail.body().contains("/unsubscribe?token="));
        assertNotEquals(unsubscribeTokenFrom(charlieMail.body()), unsubscribeTokenFrom(daveMail.body()));
    }

    //--------------------------------------------------------------

    @Test
    void emptyTemplateValuesRenderAsEmptyStrings() {
        newsletterService.send(NewsletterRequest.builder()
                .audience("INDIVIDUAL")
                .templateId(template.getId())
                .recipientIds(List.of(emptyCompany.getId()))
                .build());

        assertEquals("Hej Empty Company", emailService.sent().getFirst().subject());
        assertTrue(emailService.sent().getFirst().body().contains("Firma: \n"));
    }

    //--------------------------------------------------------------

    @Test
    void publicUnsubscribeTokenUnsubscribesCorrectUserOnlyAndKeepsAccountState() {
        newsletterService.send(NewsletterRequest.builder()
                .audience("INDIVIDUAL")
                .templateId(template.getId())
                .recipientIds(List.of(alice.getId(), charlie.getId()))
                .build());
        String token = unsubscribeTokenFrom(emailService.sent().getFirst().body());

        newsletterService.unsubscribe(Map.of("token", token));

        assertFalse(reloadUser(alice.getId()).isAcceptMarketing());
        assertTrue(reloadUser(charlie.getId()).isAcceptMarketing());
        assertTrue(reloadUser(alice.getId()).isEmailNotifications());
        assertNotNull(reloadUser(alice.getId()));
    }

    //--------------------------------------------------------------

    @Test
    void invalidExpiredAndRevokedTokensAreRejectedWithoutConsentChanges() {
        String token = tokenService.issueToken(alice);
        UnsubscribeToken persisted = tokensFor(alice).getFirst();
        expire(persisted);
        error(400, () -> newsletterService.unsubscribe(Map.of("token", token)));
        assertTrue(reloadUser(alice.getId()).isAcceptMarketing());

        String revoked = tokenService.issueToken(alice);
        tokensFor(alice).forEach(this::revoke);
        error(400, () -> newsletterService.unsubscribe(Map.of("token", revoked)));
        assertTrue(reloadUser(alice.getId()).isAcceptMarketing());

        error(400, () -> newsletterService.unsubscribe(Map.of("token", "not-a-real-token")));
        assertTrue(reloadUser(alice.getId()).isAcceptMarketing());
    }

    //--------------------------------------------------------------

    @Test
    void issuingNewTokenRevokesPreviousActiveTokenForSameUser() {
        String first = tokenService.issueToken(alice);
        String second = tokenService.issueToken(alice);

        error(400, () -> newsletterService.unsubscribe(Map.of("token", first)));
        newsletterService.unsubscribe(Map.of("token", second));

        assertFalse(reloadUser(alice.getId()).isAcceptMarketing());
        assertEquals(2, tokensFor(alice).size());
        assertTrue(tokensFor(alice).stream().allMatch(UnsubscribeToken::isRevoked));
    }

    //--------------------------------------------------------------

    @Test
    void sendCountsDeliveryFailuresWithoutCountingAsSent() {
        emailService.failWith(new ApiException(500, "mail failed"));

        NewsletterResponse response = newsletterService.send(NewsletterRequest.builder()
                .audience("INDIVIDUAL")
                .templateId(template.getId())
                .recipientIds(List.of(alice.getId(), bob.getId()))
                .build());

        assertCounts(response, 0, 1, 1);
    }

    //--------------------------------------------------------------

    @Test
    void rejectsInvalidRequests() {
        error(400, () -> newsletterService.send(null));
        error(400, () -> newsletterService.send(NewsletterRequest.builder().templateId(template.getId()).build()));
        error(400, () -> newsletterService.send(NewsletterRequest.builder()
                .audience("UNKNOWN").templateId(template.getId()).build()));
        error(400, () -> newsletterService.send(NewsletterRequest.builder().audience("ALL_APPLICANTS").build()));
        error(404, () -> newsletterService.send(NewsletterRequest.builder()
                .audience("ALL_APPLICANTS").templateId(UUID.randomUUID()).build()));
        error(400, () -> newsletterService.send(NewsletterRequest.builder()
                .audience("INDIVIDUAL").templateId(template.getId()).build()));
        error(400, () -> newsletterService.send(NewsletterRequest.builder()
                .audience("INDIVIDUAL").templateId(template.getId()).recipientIds(List.of()).build()));
        error(404, () -> newsletterService.send(NewsletterRequest.builder()
                .audience("INDIVIDUAL").templateId(template.getId()).recipientIds(List.of(UUID.randomUUID())).build()));
        error(400, () -> newsletterService.send(NewsletterRequest.builder()
                .audience("CATEGORY").templateId(template.getId()).build()));
        error(400, () -> newsletterService.send(NewsletterRequest.builder()
                .audience("CATEGORY").templateId(template.getId()).category("Z").build()));
        error(400, () -> newsletterService.send(NewsletterRequest.builder()
                .audience("ALL_APPLICANTS").templateId(template.getId()).category("A").build()));
    }

    //--------------------------------------------------------------

    private List<UUID> sentIds() {
        return emailService.sent().stream().map(SentNewsletter::recipientId).toList();
    }

    //--------------------------------------------------------------

    private void assertCounts(NewsletterResponse response, int sentTo, int skippedNoConsent, int failedToSend) {
        assertEquals(sentTo, response.getSentTo());
        assertEquals(skippedNoConsent, response.getSkippedNoConsent());
        assertEquals(failedToSend, response.getFailedToSend());
    }

    //--------------------------------------------------------------

    private void error(int status, Runnable operation) {
        assertEquals(status, assertThrows(ApiException.class, operation::run).getStatus());
    }
}
