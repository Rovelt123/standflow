package app.mappers;

import app.dtos.UserDTO;
import app.entities.User;
import app.mappers.generic.IMapper;


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
                .acceptTerms(dto.isAcceptTerms())
                .acceptPrivacy(dto.isAcceptPrivacy())
                .acceptMarketing(dto.isAcceptMarketing())
                .emailNotifications(dto.isEmailNotifications())
                .tokenVersion(dto.getTokenVersion())
                .roles(dto.getRoles())
                .build();
    }

    //--------------------------------------------------------------

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
                .acceptTerms(entity.isAcceptTerms())
                .acceptPrivacy(entity.isAcceptPrivacy())
                .acceptMarketing(entity.isAcceptMarketing())
                .emailNotifications(entity.isEmailNotifications())
                .tokenVersion(entity.getTokenVersion())
                .roles(entity.getRoles())
                .build();
    }
}
