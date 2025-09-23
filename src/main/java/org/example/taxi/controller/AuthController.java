package org.example.taxi.controller;

import org.example.taxi.config.UserSessionService;
import org.example.taxi.entity.User;
import org.example.taxi.service.TelegramBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// LoginRequest record remains for documentation/consistency
record LoginRequest(String phoneNumber, String password) {}
@RestController
@RequestMapping("/api/public")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserSessionService userSessionService; // Inject UserSessionService
    @Autowired
    private TelegramBotService telegramBotService; // Inject TelegramBotService

    /**
     * Endpoint to generate a unique registration session ID for the client.
     * The client (web/mobile app) calls this first to get a sessionId.
     * This sessionId is then used to construct the deep link for the Telegram bot.
     *
     * @param userType The type of user to register (CLIENT or DRIVER).
     * @return A map containing the generated sessionId.
     */
    @GetMapping("/register-session")
    public ResponseEntity<?> generateRegistrationSession(@RequestParam String userType) {
        User.UserType parsedUserType;
        try {
            parsedUserType = User.UserType.valueOf(userType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user type. Must be CLIENT or DRIVER."));
        }

        String sessionId = userSessionService.createRegistrationSession(parsedUserType);
        String botUsername = "ideal_taxi_user_bot"; // Replace with your actual bot username
        String deepLink = String.format("https://t.me/%s?start=register_%s_%s", botUsername, userType.toLowerCase(), sessionId);

        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("deepLink", deepLink);
        response.put("message", "Use this deep link to continue registration in Telegram. The link expires in 10 minutes.");
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint for the client (web/mobile app) to retrieve the generated credentials
     * after the user has completed the interaction with the Telegram bot.
     * This finalizes the registration.
     *
     * @param sessionId The unique ID previously generated.
     * @return A map containing the registered user's phone number and plaintext password.
     */
    @GetMapping("/get-credentials/{sessionId}")
    public ResponseEntity<?> getCredentials(@PathVariable String sessionId) {
        try {
            // This method in TelegramBotService will finalize registration,
            // send password to Telegram bot, and return phone/password to the app.
            Map<String, String> credentials = telegramBotService.finalizeRegistrationAndGetCredentials(sessionId);

            return ResponseEntity.ok(credentials);
        } catch (ResponseStatusException e) {
            logger.error("Error retrieving credentials for session {}: {}", sessionId, e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            logger.error("An unexpected error occurred while retrieving credentials for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }


    /**
     * This /login endpoint confirms successful Basic Authentication.
     * ... (unchanged from previous version) ...
     */
    @PostMapping("/login")
    public ResponseEntity<?> login() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successful via Basic Authentication.");
            response.put("phoneNumber", userDetails.getUsername());
            response.put("roles", authentication.getAuthorities().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()));

            logger.info("User {} successfully logged in with roles: {}", userDetails.getUsername(), authentication.getAuthorities());
            return ResponseEntity.ok(response);
        } else {
            logger.warn("Attempt to access /login endpoint without successful authentication detected. This is unexpected.");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized access. Provide valid Basic Auth credentials."));
        }
    }
}