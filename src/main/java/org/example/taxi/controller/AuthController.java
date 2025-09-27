package org.example.taxi.controller;

import org.example.taxi.bot.UserSessionService;
import org.example.taxi.config.JwtTokenProvider;
import org.example.taxi.entity.User;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.TelegramBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

record LoginRequest(String phoneNumber, String password) {}

@RestController
@RequestMapping("/api/public")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserSessionService userSessionService;
    @Autowired
    private TelegramBotService telegramBotService;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private  UserRepository userRepository;

    @GetMapping("/register-session")
    public ResponseEntity<?> generateRegistrationSession(@RequestParam String userType) {
        User.UserType parsedUserType;
        try {
            parsedUserType = User.UserType.valueOf(userType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user type. Must be CLIENT or DRIVER."));
        }

        String sessionId = userSessionService.createRegistrationSession(parsedUserType);
        String botUsername = "ideal_taxi_user_bot";
        String deepLink = String.format("https://t.me/%s?start=register_%s_%s", botUsername, userType.toLowerCase(), sessionId);

        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("deepLink", deepLink);
        response.put("message", "Use this deep link to continue registration in Telegram. The link expires in 10 minutes.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/get-credentials/{sessionId}")
    public ResponseEntity<?> getCredentials(@PathVariable String sessionId) {
        try {
            Map<String, String> credentials = telegramBotService.finalizeRegistrationAndGetCredentials(sessionId);
            String phoneNumber = credentials.get("phoneNumber");
            String password = credentials.get("password");
            User user = userRepository.findByPhoneNumber(phoneNumber).orElseThrow();

            String role = credentials.getOrDefault("role", user.getUserType().name());
            String token = jwtTokenProvider.generateToken(phoneNumber, role);

            Map<String, String> response = new HashMap<>();
            response.put("phoneNumber", phoneNumber);
            response.put("password", password);
            response.put("token", token);
            response.put("message", "Registration completed. Use the JWT token for authentication.");

            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            logger.error("Error retrieving credentials for session {}: {}", sessionId, e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            logger.error("An unexpected error occurred while retrieving credentials for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.phoneNumber(), loginRequest.password())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String phoneNumber = loginRequest.phoneNumber();
            User user = userRepository.findByPhoneNumber(phoneNumber).orElseThrow();
            String role = authentication.getAuthorities().stream()
                    .map(Object::toString)
                    .map(auth -> auth.replace("ROLE_", ""))
                    .findFirst()
                    .orElse(user.getUserType().name()); // Default to CLIENT if no role found
            String token = jwtTokenProvider.generateToken(phoneNumber, role);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successful.");
            response.put("phoneNumber", phoneNumber);
            response.put("token", token);
            response.put("roles", authentication.getAuthorities().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()));

            logger.info("User {} successfully logged in with roles: {}", phoneNumber, authentication.getAuthorities());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Login failed for user {}: {}", loginRequest.phoneNumber(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid phone number or password."));
        }
    }
}