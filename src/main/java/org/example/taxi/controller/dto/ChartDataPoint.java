package org.example.taxi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataPoint {
    private String name; // e.g., "Jan", "Completed", "Yunusabad District"
    private Number value; // e.g., count, earnings, rating
}