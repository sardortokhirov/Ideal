package org.example.taxi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.taxi.entity.Price;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceResponse {
    private Long id;
    private Long fromDistrictId;
    private String fromDistrictName;
    private Long toDistrictId;
    private String toDistrictName;
    private BigDecimal basePricePerSeat;
    private BigDecimal womenDriverPricePerSeat;
    private BigDecimal premiumPricePerSeat;
    private BigDecimal frontSeatExtraFee;
    private BigDecimal otherSeatExtraFee;
    private BigDecimal luggagePrice;

    public static PriceResponse fromEntity(Price price) {
        if (price == null) return null;
        return new PriceResponse(
                price.getId(),
                price.getFromDistrict() != null ? price.getFromDistrict().getId() : null,
                price.getFromDistrict() != null ? price.getFromDistrict().getName() : null,
                price.getToDistrict() != null ? price.getToDistrict().getId() : null,
                price.getToDistrict() != null ? price.getToDistrict().getName() : null,
                price.getBasePricePerSeat(),
                price.getWomenDriverPricePerSeat(),
                price.getPremiumPricePerSeat(),
                price.getFrontSeatExtraFee(),
                price.getOtherSeatExtraFee(),
                price.getLuggagePrice()
        );
    }
}