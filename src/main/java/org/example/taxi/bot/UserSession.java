package org.example.taxi.bot;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserSession {
    private Long chatId;
    private String state;
    private List<String> navigationStates = new ArrayList<>();
    private List<Integer> messageIds = new ArrayList<>();
    private String currentRegistrationSessionId; // New field to link to web-initiated registration
}