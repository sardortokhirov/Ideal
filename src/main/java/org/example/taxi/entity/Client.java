package org.example.taxi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(nullable = true)
    private String firstName;
    @Column(nullable = true)
    private String lastName;
    @Column(nullable = true)
    private String profilePictureUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    private String preferences;

    @Enumerated(EnumType.STRING)
    private ClientOrderSource orderSource = ClientOrderSource.MOBILE_APP;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ClientOrderSource {
        MOBILE_APP,
        OPERATOR
    }

    public Long getDistrictId() {
        return this.district != null ? this.district.getId() : null;
    }
}