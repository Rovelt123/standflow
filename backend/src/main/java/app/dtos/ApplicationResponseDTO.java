package app.dtos;

import app.enums.ApplicationStatus;

import java.util.UUID;

public record ApplicationResponseDTO(UUID id, ApplicationStatus status, String createdAt) {
}
