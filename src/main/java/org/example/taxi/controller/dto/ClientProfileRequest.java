package org.example.taxi.controller.dto;

import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class ClientProfileRequest {
    @Nullable private String firstName;
    @Nullable private String lastName;
    @Nullable private String preferences;
}