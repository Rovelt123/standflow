package app.mappers;

import app.dtos.UserDTO;
import app.entities.User;

import java.util.Set;

public class UserMapper {

    public UserDTO toDTO(User entity) {
        if (entity == null) {
            return null;
        }

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
                .roles(Set.copyOf(entity.getRoles()))
                .build();
    }
}
