package org.example.taxi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Price {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_district_id", nullable = false)
    private District fromDistrict;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_district_id", nullable = false)
    private District toDistrict;

    @Column(nullable = false)
    private BigDecimal basePricePerSeat;

    @Column(nullable = false)
    private BigDecimal womenDriverPricePerSeat;

    @Column(nullable = false)
    private BigDecimal premiumPricePerSeat;

    @Column(nullable = false)
    private BigDecimal frontSeatExtraFee;

    @Column(nullable = false)
    private BigDecimal otherSeatExtraFee;

    @Column(nullable = false)
    private BigDecimal luggagePrice;

    @PrePersist
    @PreUpdate
    private void validateDistricts() {
        if (fromDistrict.equals(toDistrict)) {
            throw new IllegalArgumentException("From and To districts cannot be the same for a price route.");
        }
    }
}