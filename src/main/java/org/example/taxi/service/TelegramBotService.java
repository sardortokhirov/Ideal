package org.example.taxi.service;

import org.example.taxi.config.UserSessionService;
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

    // Define states
    public static final String STATE_REGISTER_AWAITING_PHONE = "REGISTER_AWAITING_PHONE";
    public static final String STATE_FORGOT_PASSWORD_AWAITING_PHONE = "FORGOT_PASSWORD_AWAITING_PHONE";


    /**
     * Initializes a Telegram bot registration flow linked to a web-generated session ID.
     * Stores the chatId in the registration cache and links it to the bot's UserSession.
     *
     * @param sessionId The unique ID from the web app.
     * @param chatId The Telegram chat ID.
     * @return A message to display to the user.
     */
    public String startRegistrationWithSessionId(String sessionId, Long chatId) {
        Optional<RegistrationCacheEntry> entryOpt = userSessionService.getRegistrationCacheEntry(sessionId);

        if (entryOpt.isEmpty()) {
            userSessionService.clearSession(chatId);
            return "This registration link is invalid or has expired. Please get a new link from the app.";
        }

        RegistrationCacheEntry entry = entryOpt.get();

        if (entry.getChatId() != null && !entry.getChatId().equals(chatId)) {
            userSessionService.clearRegistrationSession(sessionId);
            userSessionService.clearSession(chatId);
            return "This registration link has already been used or is active in another chat. Please get a new link.";
        }

        // Ensure the RegistrationCacheEntry is updated with the chatId
        if (entry.getChatId() == null) {
            entry.setChatId(chatId);
            userSessionService.updateRegistrationCacheEntry(entry);
            logger.info("Registration session {} linked to chatId {}", sessionId, chatId);
        }

        // --- UPDATED TO USE NEW METHOD ---
        userSessionService.linkChatToRegistrationSession(chatId, sessionId);
        // --- END UPDATE ---

        userSessionService.setUserState(chatId, STATE_REGISTER_AWAITING_PHONE);
        return "You are registering as a *" + entry.getUserType().name() + "*.";
    }

    /**
     * Processes the phone number provided by the Telegram user for a web-initiated registration session.
     * Saves the phone number and generated password temporarily in the cache.
     *
     * @param sessionId The unique ID from the web app.
     * @param phoneNumber The user's phone number.
     * @return A message to display to the user.
     */
    public String processPhoneNumberForSessionId(String sessionId, String phoneNumber) {
        Optional<RegistrationCacheEntry> entryOpt = userSessionService.getRegistrationCacheEntry(sessionId);

        if (entryOpt.isEmpty()) {
            return "This registration session has expired. Please get a new link from the app.";
        }

        RegistrationCacheEntry entry = entryOpt.get();
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);

        if (userRepository.existsByPhoneNumber(normalizedPhoneNumber)) {
            userSessionService.clearRegistrationSession(sessionId);
            userSessionService.clearSession(entry.getChatId());
            return "An account with this phone number already exists. Please use 'Forgot Password' in the app.";
        }

        entry.setPhoneNumber(normalizedPhoneNumber);
        String generatedRawPassword = generateRandomPassword();
        entry.setGeneratedPassword(generatedRawPassword);
        userSessionService.updateRegistrationCacheEntry(entry);

        logger.info("Phone number {} received and stored for registration session {}. Waiting for app to finalize.", normalizedPhoneNumber, sessionId);
        return "Thank you! Your phone number (" + normalizedPhoneNumber + ") is now linked to your registration. Please return to the app and click 'Get Password' to finalize your registration. \n_Your generated password is: *" + generatedRawPassword + "*_";
    }


    /**
     * Finalizes the registration process based on cached data and returns credentials to the app.
     * This method is called by the AuthController (from the web/app).
     *
     * @param sessionId The unique ID from the web app.
     * @return A map containing the user's phone number and the *plaintext* generated password.
     */
    @Transactional
    public Map<String, String> finalizeRegistrationAndGetCredentials(String sessionId) {
        Optional<RegistrationCacheEntry> entryOpt = userSessionService.getRegistrationCacheEntry(sessionId);

        if (entryOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration session invalid or expired.");
        }

        RegistrationCacheEntry entry = entryOpt.get();

        if (entry.getPhoneNumber() == null || entry.getChatId() == null || entry.getGeneratedPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number, Telegram chat, or generated password not linked yet. Please complete bot interaction.");
        }

        if (userRepository.existsByPhoneNumber(entry.getPhoneNumber())) {
            userSessionService.clearRegistrationSession(sessionId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this phone number already exists.");
        }
        if (userRepository.existsByChatId(entry.getChatId())) {
            userSessionService.clearRegistrationSession(sessionId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Telegram account already linked to another user. If this is a mistake, please use forgot password or contact support.");
        }

        String generatedRawPassword = entry.getGeneratedPassword();
        String encodedPassword = passwordEncoder.encode(generatedRawPassword);

        User newUser = new User();
        newUser.setChatId(entry.getChatId());
        newUser.setPhoneNumber(entry.getPhoneNumber());
        newUser.setPassword(encodedPassword);
        newUser.setUserType(entry.getUserType());
        newUser = userRepository.save(newUser);

        if (User.UserType.DRIVER.equals(entry.getUserType())) {
            Driver driver = new Driver();
            driver.setUser(newUser);
            driverRepository.save(driver);
            logger.info("New DRIVER registered: userId={}, phoneNumber={}", newUser.getId(), entry.getPhoneNumber());
        } else if (User.UserType.CLIENT.equals(entry.getUserType())) {
            Client client = new Client();
            client.setUser(newUser);
            clientRepository.save(client);
            logger.info("New CLIENT registered: userId={}, phoneNumber={}", newUser.getId(), entry.getPhoneNumber());
        } else {
            logger.error("UserType {} not supported during bot registration for userId={}", entry.getUserType(), newUser.getId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid user type specified during registration.");
        }

        Map<String, String> credentials = new HashMap<>();
        credentials.put("phoneNumber", entry.getPhoneNumber());
        credentials.put("password", generatedRawPassword);

        userSessionService.clearRegistrationSession(sessionId);
        userSessionService.clearSession(entry.getChatId());

        return credentials;
    }


    /**
     * Handles forgot password process.
     * @param chatId The Telegram chat ID.
     * @param phoneNumber The user's phone number.
     * @return A message containing the new generated password or an error.
     */
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