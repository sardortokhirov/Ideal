package org.example.taxi.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long driverId;
    private String model;
    private int year;
    private String photoUrl;
    private int seatCapacity;
}