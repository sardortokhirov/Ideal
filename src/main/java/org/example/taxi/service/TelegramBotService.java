package org.example.taxi.service;

import org.example.taxi.bot.UserSessionService;
import org.example.taxi.entity.Client;
import org.example.taxi.entity.Driver;
import org.example.taxi.entity.User;
import org.example.taxi.repository.ClientRepository;
import org.example.taxi.repository.DriverRepository;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.cache.RegistrationCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class TelegramBotService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private UserSessionService userSessionService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public static final String STATE_AWAITING_PHONE = "AWAITING_PHONE";
    public static final String STATE_FORGOT_PASSWORD_AWAITING_PHONE = "FORGOT_PASSWORD_AWAITING_PHONE";

    public String startRegistrationOrLoginWithSessionId(String sessionId, Long chatId) {
        Optional<RegistrationCacheEntry> entryOpt = userSessionService.getRegistrationCacheEntry(sessionId);

        if (entryOpt.isEmpty()) {
            userSessionService.clearSession(chatId);
            return "This link is invalid or has expired. Please get a new link from the app.";
        }

        RegistrationCacheEntry entry = entryOpt.get();

        if (entry.getChatId() != null && !entry.getChatId().equals(chatId)) {
            userSessionService.clearRegistrationSession(sessionId);
            userSessionService.clearSession(chatId);
            return "This link has already been used or is active in another chat. Please get a new link.";
        }

        if (entry.getChatId() == null) {
            entry.setChatId(chatId);
            userSessionService.updateRegistrationCacheEntry(entry);
            logger.info("Session {} linked to chatId {}", sessionId, chatId);
        }

        userSessionService.linkChatToRegistrationSession(chatId, sessionId);
        userSessionService.setUserState(chatId, STATE_AWAITING_PHONE);
        return "Please provide your phone number to register or log in as a *" + entry.getUserType().name() + "*.";
    }

    public String processPhoneNumberForSessionId(String sessionId, String phoneNumber, Long chatId) {
        Optional<RegistrationCacheEntry> entryOpt = userSessionService.getRegistrationCacheEntry(sessionId);

        if (entryOpt.isEmpty()) {
            return "This session has expired. Please get a new link from the app.";
        }

        RegistrationCacheEntry entry = entryOpt.get();
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);

        Optional<User> existingUserOpt = userRepository.findByPhoneNumber(normalizedPhoneNumber);
        if (existingUserOpt.isPresent()) {
            User user = existingUserOpt.get();
            user.setChatId(chatId);
            userRepository.save(user);
            entry.setPhoneNumber(normalizedPhoneNumber);
            // Do not generate or store a new password for existing users
            userSessionService.updateRegistrationCacheEntry(entry);
            logger.info("User logged in: phoneNumber={}, chatId={}", normalizedPhoneNumber, chatId);
            return "Successfully logged in with phone number: " + normalizedPhoneNumber + ". Please return to the app and click 'Get Credentials' to proceed.";
        }

        entry.setPhoneNumber(normalizedPhoneNumber);
        String generatedRawPassword = generateRandomPassword();
        entry.setGeneratedPassword(generatedRawPassword);
        userSessionService.updateRegistrationCacheEntry(entry);

        logger.info("Phone number {} received for registration session {}. Waiting for app to finalize.", normalizedPhoneNumber, sessionId);
        return "Thank you! Your phone number (" + normalizedPhoneNumber + ") is now linked to your registration. Please return to the app and click 'Get Password' to finalize your registration. \n_Your generated password is: *" + generatedRawPassword + "*_";
    }

    @Transactional
    public Map<String, String> finalizeRegistrationAndGetCredentials(String sessionId) {
        Optional<RegistrationCacheEntry> entryOpt = userSessionService.getRegistrationCacheEntry(sessionId);

        if (entryOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session invalid or expired.");
        }

        RegistrationCacheEntry entry = entryOpt.get();

        if (entry.getPhoneNumber() == null || entry.getChatId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number or Telegram chat not linked. Please complete bot interaction.");
        }

        String normalizedPhoneNumber = entry.getPhoneNumber();
        Optional<User> existingUserOpt = userRepository.findByPhoneNumber(normalizedPhoneNumber);

        if (existingUserOpt.isPresent()) {
            Map<String, String> credentials = new HashMap<>();
            credentials.put("phoneNumber", normalizedPhoneNumber);
            // Do not include password for existing users
            userSessionService.clearRegistrationSession(sessionId);
            userSessionService.clearSession(entry.getChatId());
            logger.info("Credentials retrieved for existing user: phoneNumber={}", normalizedPhoneNumber);
            return credentials;
        }

        if (userRepository.existsByChatId(entry.getChatId())) {
            userSessionService.clearRegistrationSession(sessionId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Telegram account already linked to another user. Use forgot password or contact support.");
        }

        if (entry.getGeneratedPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Generated password not found. Please complete bot interaction.");
        }

        String generatedRawPassword = entry.getGeneratedPassword();
        String encodedPassword = passwordEncoder.encode(generatedRawPassword);

        User newUser = new User();
        newUser.setChatId(entry.getChatId());
        newUser.setPhoneNumber(normalizedPhoneNumber);
        newUser.setPassword(encodedPassword);
        newUser.setUserType(entry.getUserType());
        newUser = userRepository.save(newUser);

        if (User.UserType.DRIVER.equals(entry.getUserType())) {
            Driver driver = new Driver();
            driver.setUser(newUser);
            driverRepository.save(driver);
            logger.info("New DRIVER registered: userId={}, phoneNumber={}", newUser.getId(), normalizedPhoneNumber);
        } else if (User.UserType.CLIENT.equals(entry.getUserType())) {
            Client client = new Client();
            client.setUser(newUser);
            clientRepository.save(client);
            logger.info("New CLIENT registered: userId={}, phoneNumber={}", newUser.getId(), normalizedPhoneNumber);
        } else {
            logger.error("UserType {} not supported during bot registration for userId={}", entry.getUserType(), newUser.getId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid user type specified during registration.");
        }

        Map<String, String> credentials = new HashMap<>();
        credentials.put("phoneNumber", normalizedPhoneNumber);
        credentials.put("password", generatedRawPassword);

        userSessionService.clearRegistrationSession(sessionId);
        userSessionService.clearSession(entry.getChatId());

        return credentials;
    }

    @Transactional
    public String resetPassword(Long chatId, String phoneNumber) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);

        Optional<User> existingUserOpt = userRepository.findByPhoneNumber(normalizedPhoneNumber);
        if (existingUserOpt.isEmpty()) {
            userSessionService.clearSession(chatId);
            return "No account found with this phone number. Please register first using /start register_client or /start register_driver.";
        }

        User user = existingUserOpt.get();
        if (user.getChatId() != null && !user.getChatId().equals(chatId)) {
            logger.warn("Password reset attempt for phoneNumber {} from a new chatId {}. Old chatId: {}", normalizedPhoneNumber, chatId, user.getChatId());
        }

        String newRawPassword = generateRandomPassword();
        String newEncodedPassword = passwordEncoder.encode(newRawPassword);
        user.setPassword(newEncodedPassword);
        user.setChatId(chatId);
        userRepository.save(user);
        logger.info("Password reset for user: phoneNumber={}", normalizedPhoneNumber);
        userSessionService.clearSession(chatId);
        return "Your new password is: *" + newRawPassword + "*\n\nPlease keep it safe.";
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        String cleanNumber = phoneNumber.replaceAll("[^0-9+]", "");
        if (!cleanNumber.startsWith("+") && cleanNumber.matches("\\d+")) {
            return "+" + cleanNumber;
        }
        return cleanNumber;
    }
}