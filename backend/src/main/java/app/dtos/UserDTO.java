package app.dtos;

import app.enums.Role;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class UserDTO {
    private final UUID id;
    private final String company;
    private final String firstname;
    private final String lastname;
    private final String cvr;
    private final String email;
    private final String phone;
    private final String address;
    private final String city;
    private final Set<Role> roles;
}
