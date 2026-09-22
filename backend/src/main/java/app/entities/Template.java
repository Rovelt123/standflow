package app.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "template")
@Getter
@Setter
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


}
