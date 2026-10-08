package app.dtos;

import lombok.*;

/** Public view of an accepted stand holder for the Stadeholdere page. No personal data. */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class VendorDTO {

    private String company;
    private String description;
    private String website;
    private String standType;
}
