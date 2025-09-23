package org.example.taxi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime; // Added for User createdAt

@Data
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String phoneNumber;
    @Column(nullable = false)
    private String password;
    private Long chatId;

    @Column(nullable = true)
    private String firstName;
    @Column(nullable = true)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType;

    private LocalDateTime createdAt = LocalDateTime.now(); // Ensure createdAt is present for analytics

    public enum UserType {
        CLIENT,
        DRIVER,
        OPERATOR,
        ADMIN,
        ETAMIN // NEW
    }
}