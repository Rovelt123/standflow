package app.services;

import app.daos.MessageDAO;
import app.dtos.MessageDTO;
import app.dtos.ThreadDTO;
import app.entities.Message;
import app.entities.User;
import app.enums.Role;
import app.exceptions.ApiException;
import app.mappers.MessageMapper;
import app.server.Setup;
import app.utils.ErrorHandler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

public class ConversationService {
    private final MessageDAO messageDAO;
    private final MessageMapper mapper = new MessageMapper();
    private final Clock clock;
    private final MessageEmailService messageEmailService;

    //--------------------------------------------------------------

    public ConversationService() {
        this(new MessageDAO(Setup.em));
    }

    //--------------------------------------------------------------

    public ConversationService(MessageDAO messageDAO) {
        this(messageDAO, Clock.systemDefaultZone());
    }

    //--------------------------------------------------------------

    public ConversationService(MessageDAO messageDAO, Clock clock) {
        this(messageDAO, clock, new GmailSmtpMessageEmailService());
    }

    //--------------------------------------------------------------

    public ConversationService(MessageDAO messageDAO, Clock clock, MessageEmailService messageEmailService) {
        this.messageDAO = Objects.requireNonNull(messageDAO);
        this.clock = Objects.requireNonNull(clock);
        this.messageEmailService = Objects.requireNonNull(messageEmailService);
    }

    //--------------------------------------------------------------

    public MessageDTO sendCustomer(UUID authenticatedId, Map<String, ?> request) {
        User customer = authenticatedCustomer(authenticatedId);
        User admin = chatAdmin();
        validateFields(request, Set.of("subject", "body"));
        return mapper.toDTO(save(customer, admin, requiredText(request, "subject", 200),
                requiredText(request, "body", 5000)));
    }

    //--------------------------------------------------------------

    public List<MessageDTO> getMine(UUID authenticatedId) {
        User customer = authenticatedCustomer(authenticatedId);
        User admin = chatAdmin();
        return messages(customer.getId(), admin.getId());
    }

    //--------------------------------------------------------------

    public List<MessageDTO> getThread(UUID authenticatedId, UUID customerId) {
        User admin = authenticatedAdmin(authenticatedId);
        User customer = customer(customerId);
        return messages(customer.getId(), admin.getId());
    }

    //--------------------------------------------------------------

    public MessageDTO sendAdmin(UUID authenticatedId, UUID customerId, Map<String, ?> request) {
        User admin = authenticatedAdmin(authenticatedId);
        User customer = customer(customerId);
        List<Message> conversation = messageDAO.getConversation(customer.getId(), admin.getId());
        validateFields(request, conversation.isEmpty() ? Set.of("subject", "body") : Set.of("body"));
        String subject = conversation.isEmpty() ? requiredText(request, "subject", 200)
                : conversation.getLast().getSubject();
        Message message = save(admin, customer, subject, requiredText(request, "body", 5000));
        messageEmailService.sendAdminMessageNotification(customer, message);
        return mapper.toDTO(message);
    }

    //--------------------------------------------------------------

    public void markRead(UUID authenticatedId, UUID customerId) {
        User admin = authenticatedAdmin(authenticatedId);
        User customer = customer(customerId);
        messageDAO.markRead(customer.getId(), admin.getId());
    }

    //--------------------------------------------------------------

    public List<ThreadDTO> getThreads(UUID authenticatedId, String sort) {
        User admin = authenticatedAdmin(authenticatedId);
        String ordering = sort == null ? "date" : sort;
        if (!Set.of("name", "company", "subject", "date").contains(ordering)) {
            throw new ApiException(400, "Ugyldig sortering.");
        }
        Map<UUID, ThreadDTO> threads = new LinkedHashMap<>();
        for (Message message : messageDAO.getAdminMessages(admin.getId())) {
            boolean incoming = message.getRecipient().getId().equals(admin.getId());
            User customer = incoming ? message.getSender() : message.getRecipient();
            if (!isCustomer(customer)) continue;
            ThreadDTO previous = threads.get(customer.getId());
            threads.put(customer.getId(), ThreadDTO.builder()
                    .customerId(customer.getId())
                    .customerName(customer.getFirstname() + " " + customer.getLastname())
                    .company(customer.getCompany())
                    .subject(message.getSubject())
                    .lastMessage(message.getBody())
                    .lastMessageAt(message.getCreatedAt().toString())
                    .unread((previous != null && previous.isUnread()) || (incoming && !message.isRead()))
                    .build());
        }
        Comparator<ThreadDTO> comparator = switch (ordering) {
            case "name" -> Comparator.comparing(ThreadDTO::getCustomerName, String.CASE_INSENSITIVE_ORDER);
            case "company" -> Comparator.comparing(ThreadDTO::getCompany, String.CASE_INSENSITIVE_ORDER);
            case "subject" -> Comparator.comparing(ThreadDTO::getSubject, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing((ThreadDTO thread) -> LocalDateTime.parse(thread.getLastMessageAt())).reversed();
        };
        return threads.values().stream().sorted(comparator.thenComparing(ThreadDTO::getCustomerId)).toList();
    }

    //--------------------------------------------------------------

    private List<MessageDTO> messages(UUID customerId, UUID adminId) {
        return messageDAO.getConversation(customerId, adminId).stream().map(mapper::toDTO).toList();
    }

    //--------------------------------------------------------------

    private Message save(User sender, User recipient, String subject, String body) {
        Message message = mapper.toEntity(MessageDTO.builder()
                .senderId(sender.getId()).recipientId(recipient.getId())
                .subject(subject).body(body).createdAt(LocalDateTime.now(clock).toString()).read(false).build());
        message.setSender(sender);
        message.setRecipient(recipient);
        try {
            return messageDAO.create(message);
        } catch (RuntimeException exception) {
            throw new ApiException(500, "Beskeden kunne ikke gemmes.");
        }
    }

    //--------------------------------------------------------------

    private User chatAdmin() {
        List<User> admins = messageDAO.getAdmins();
        if (admins.size() != 1) throw new ApiException(500, "Chatten kræver præcis én admin.");
        return admins.getFirst();
    }

    //--------------------------------------------------------------

    private User authenticatedAdmin(UUID id) {
        User admin = chatAdmin();
        if (!admin.getId().equals(id)) throw new ApiException(403, "Kun chat-admin har adgang.");
        return admin;
    }

    //--------------------------------------------------------------

    private User authenticatedCustomer(UUID id) {
        User user = id == null ? null : messageDAO.getUser(id);
        if (!isCustomer(user)) throw new ApiException(403, "Kun kunder har adgang.");
        return user;
    }

    //--------------------------------------------------------------

    private User customer(UUID id) {
        if (id == null) throw new ApiException(400, "Kundens id er ugyldigt.");
        User user = ErrorHandler.tryEntity(messageDAO.getUser(id), "Kunden findes ikke.");
        if (!isCustomer(user)) throw new ApiException(400, "Brugeren er ikke en kunde.");
        return user;
    }

    //--------------------------------------------------------------

    private boolean isCustomer(User user) {
        return user != null && user.getRoles().contains(Role.USER)
                && !user.getRoles().contains(Role.ADMIN) && !user.getRoles().contains(Role.OWNER);
    }

    //--------------------------------------------------------------

    private void validateFields(Map<String, ?> request, Set<String> allowed) {
        if (request == null) throw new ApiException(400, "Beskeden skal være et JSON-objekt.");
        if (!allowed.containsAll(request.keySet())) throw new ApiException(400, "Beskeden indeholder ukendte felter.");
    }

    //--------------------------------------------------------------

    private String requiredText(Map<String, ?> request, String field, int maximum) {
        if (!(request.get(field) instanceof String value) || value.isBlank() || value.length() > maximum) {
            throw new ApiException(400, field + " skal være tekst på 1 til " + maximum + " tegn.");
        }
        return value;
    }
}
