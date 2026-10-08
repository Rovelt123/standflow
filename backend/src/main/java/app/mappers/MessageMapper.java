package app.mappers;

import app.dtos.MessageDTO;
import app.entities.Message;
import app.entities.User;
import app.mappers.generic.IMapper;
import app.utils.ErrorHandler;

public class MessageMapper implements IMapper<Message, MessageDTO> {
    @Override
    public Message toEntity(MessageDTO dto) {
        return Message.builder()
                .id(dto.getId())
                .sender(User.builder().id(dto.getSenderId()).build())
                .recipient(User.builder().id(dto.getRecipientId()).build())
                .subject(dto.getSubject())
                .body(dto.getBody())
                .createdAt(ErrorHandler.tryParseLocalDateTime(dto.getCreatedAt(), "Ugyldigt tidspunkt."))
                .read(dto.isRead())
                .build();
    }

    //--------------------------------------------------------------

    @Override
    public MessageDTO toDTO(Message entity) {
        return MessageDTO.builder()
                .id(entity.getId())
                .senderId(entity.getSender().getId())
                .recipientId(entity.getRecipient().getId())
                .subject(entity.getSubject())
                .body(entity.getBody())
                .createdAt(entity.getCreatedAt().toString())
                .read(entity.isRead())
                .build();
    }
}
