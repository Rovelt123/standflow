package app.dtos;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ApplicationDTO {

    private UUID id;
    private String company;
    private String contact;
    private String cvr;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String website;
    private String products;
    private Boolean previousExhibitor;
    private String standType;
    private Integer tables;
    private Integer chairs;
    private String status;
    private String createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String customerNote;
}
