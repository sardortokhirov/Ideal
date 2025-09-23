package org.example.taxi.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.taxi.entity.Client;
import org.example.taxi.entity.District; // Import District
import org.example.taxi.entity.Region;   // Import Region

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfileResponse {
    private Long id;
    private String phoneNumber;

    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private String preferences;

    private Long districtId;   // NEW
    private String districtName; // NEW
    private String regionName;   // NEW

    public static ClientProfileResponse fromEntity(Client client) {
        if (client == null) return null;
        String districtName = client.getDistrict() != null ? client.getDistrict().getName() : null;
        String regionName = (client.getDistrict() != null && client.getDistrict().getRegion() != null)
                ? client.getDistrict().getRegion().getName() : null;

        return new ClientProfileResponse(
                client.getId(),
                client.getUser() != null ? client.getUser().getPhoneNumber() : null,
                client.getFirstName(),
                client.getLastName(),
                client.getProfilePictureUrl(),
                client.getPreferences(),
                client.getDistrict() != null ? client.getDistrict().getId() : null, // NEW
                districtName, // NEW
                regionName // NEW
        );
    }
}