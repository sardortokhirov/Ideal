package org.example.taxi.bot;

import org.example.taxi.service.cache.RegistrationCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {
    private static final Logger logger = LoggerFactory.getLogger(UserSessionService.class);

    // Stores bot conversation state per Telegram chatId
    private final Map<Long, UserSession> sessionStore = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> sessionDataStore = new ConcurrentHashMap<>();

    // Stores transient registration data, keyed by the unique sessionId from the web/app
    private final Map<String, RegistrationCacheEntry> registrationCache = new ConcurrentHashMap<>();

    // --- UserSession (Bot conversation state per chatId) methods ---
    public void setUserState(Long chatId, String state) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> {
            UserSession newSession = new UserSession();
            newSession.setChatId(chatId);
            return newSession;
        });
        session.setState(state);
        saveUserSession(session);
    }

    public String getUserState(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId))
                .map(UserSession::getState)
                .orElse(null);
    }

    public void setUserData(Long chatId, String key, String value) {
        Map<String, String> data = sessionDataStore.computeIfAbsent(chatId, k -> new HashMap<>());
        data.put(key, value);
        sessionDataStore.put(chatId, data);
    }

    public String getUserData(Long chatId, String key) {
        return Optional.ofNullable(sessionDataStore.get(chatId))
                .map(data -> data.get(key))
                .orElse(null);
    }

    public void clearSession(Long chatId) {
        UserSession removedSession = sessionStore.remove(chatId);
        if (removedSession != null) {
            logger.info("Cleared bot conversation session for chatId: {}", chatId);
        }
        sessionDataStore.remove(chatId);
    }

    public List<Integer> getMessageIds(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId))
                .map(UserSession::getMessageIds)
                .orElse(new ArrayList<>());
    }

    public void clearMessageIds(Long chatId) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> {
            UserSession newSession = new UserSession();
            newSession.setChatId(chatId);
            return newSession;
        });
        session.setMessageIds(new ArrayList<>());
        saveUserSession(session);
    }

    public Optional<UserSession> getUserSession(Long chatId) {
        return Optional.ofNullable(sessionStore.get(chatId));
    }

    public void saveUserSession(UserSession session) {
        if (session.getChatId() != null) {
            sessionStore.put(session.getChatId(), session);
        }
    }

    // --- NEW METHOD FOR LINKING CHAT TO REGISTRATION SESSION ---
    /**
     * Links a Telegram chatId to a web-initiated registration sessionId.
     * Creates a new UserSession if one doesn't exist for the chatId.
     *
     * @param chatId The Telegram chat ID.
     * @param sessionId The unique registration session ID.
     */
    public void linkChatToRegistrationSession(Long chatId, String sessionId) {
        UserSession session = sessionStore.computeIfAbsent(chatId, k -> {
            UserSession newSession = new UserSession();
            newSession.setChatId(chatId);
            return newSession;
        });
        session.setCurrentRegistrationSessionId(sessionId);
        saveUserSession(session);
        logger.debug("Linked chatId {} to registration sessionId {}", chatId, sessionId);
    }
    // --- END NEW METHOD ---


    // --- RegistrationCache (Web-initiated session linking) methods ---

    public String createRegistrationSession(org.example.taxi.entity.User.UserType userType) {
        String sessionId = UUID.randomUUID().toString();
        RegistrationCacheEntry entry = new RegistrationCacheEntry();
        entry.setSessionId(sessionId);
        entry.setUserType(userType);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        registrationCache.put(sessionId, entry);
        logger.info("Created new registration session: {} for userType {}", sessionId, userType);
        return sessionId;
    }

    public Optional<RegistrationCacheEntry> getRegistrationCacheEntry(String sessionId) {
        RegistrationCacheEntry entry = registrationCache.get(sessionId);
        if (entry != null && entry.isExpired()) {
            logger.warn("Expired registration session {} detected and removed.", sessionId);
            registrationCache.remove(sessionId);
            return Optional.empty();
        }
        return Optional.ofNullable(entry);
    }

    @Transactional
    public void updateRegistrationCacheEntry(RegistrationCacheEntry entry) {
        registrationCache.put(entry.getSessionId(), entry);
        logger.debug("Updated registration cache entry for session {}. ChatId: {}, Phone: {}", entry.getSessionId(), entry.getChatId(), entry.getPhoneNumber());
    }

    public void clearRegistrationSession(String sessionId) {
        RegistrationCacheEntry removedEntry = registrationCache.remove(sessionId);
        if (removedEntry != null) {
            logger.info("Cleared registration cache entry for session: {}", sessionId);
        }
    }
}