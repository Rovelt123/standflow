package app.entities;

import app.enums.ApplicationStandType;
import app.enums.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 150)
    private String contact;

    @Column(nullable = false, length = 8)
    private String cvr;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 150)
    private String city;

    @Column(length = 255)
    private String website;

    @Column(nullable = false, length = 5000)
    private String products;

    @Column(name = "previous_exhibitor", nullable = false)
    private boolean previousExhibitor;

    @Enumerated(EnumType.STRING)
    @Column(name = "stand_type", nullable = false, length = 1)
    private ApplicationStandType standType;

    @Column(name = "table_count", nullable = false)
    private int tables;

    @Column(name = "chair_count", nullable = false)
    private int chairs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDate createdAt;
}
