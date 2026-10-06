package app.mappers;

import app.dtos.UserDTO;
import app.entities.User;
import app.enums.Role;
import app.mappers.generic.IMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Builder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


public class UserMapper implements IMapper<User, UserDTO> {

    @Override
    public User toEntity(UserDTO dto) {
        return User.builder()
                .id(dto.getId())
                .company(dto.getCompany())
                .firstname(dto.getFirstname())
                .lastname(dto.getLastname())
                .cvr(dto.getCvr())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .city(dto.getCity())
                .build();
    }

    @Override
    public UserDTO toDTO(User entity) {
        return UserDTO.builder()
                .id(entity.getId())
                .company(entity.getCompany())
                .firstname(entity.getFirstname())
                .lastname(entity.getLastname())
                .cvr(entity.getCvr())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .city(entity.getCity())
                .build();
    }
}
