package app.dtos;

import app.entities.UploadedFile;

import java.util.UUID;

public record FileDTO(UUID id, String originalName, String contentType, long size, String uploadedAt) {
    public static FileDTO from(UploadedFile file) {
        return new FileDTO(file.getId(), file.getOriginalName(), file.getContentType(), file.getSize(),
                file.getUploadedAt().toString());
    }
}
