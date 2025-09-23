package org.example.taxi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperatorResponse {
    private Long userId;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String userType;
    private String message;
}