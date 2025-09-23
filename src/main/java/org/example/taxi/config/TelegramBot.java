package org.example.taxi.config; // Keeping it in 'config' as per your original structure, but 'bot' package would be more descriptive.

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.taxi.entity.User;
import org.example.taxi.service.TelegramBotService;
import org.example.taxi.service.cache.RegistrationCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component; // Change to @Component if it's not strictly config but the bot itself
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot { // Renamed from TelegramBotConfig
    private static final Logger logger = LoggerFactory.getLogger(TelegramBot.class);

    private final TelegramBotService telegramBotService;
    private final UserSessionService userSessionService; // Inject UserSessionService

    private final MessageSender messageSender;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.name}") // Changed to username as per your config
    private String botUsername;

    @PostConstruct
    public void init() {
        messageSender.setBot(this);
        clearWebhook();
    }

    @PostConstruct
    public void clearWebhook() {
        try {
            execute(new DeleteWebhook());
            logger.info("Webhook cleared for {}", botUsername);
        } catch (TelegramApiException e) {
            logger.error("Error clearing webhook for {}: {}", botUsername, e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        if (botToken == null || botToken.isEmpty()) {
            logger.error("Bot token not set in application.properties");
            throw new IllegalStateException("Bot token is missing");
        }
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Long chatId = null;
        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
        }

        if (chatId == null) {
            logger.warn("Received update without chat ID: {}", update);
            return;
        }

        try {
            if (update.hasMessage()) {
                if (update.getMessage().hasText()) {
                    handleTextMessage(update.getMessage().getText(), chatId); // No need to pass full update
                } else if (update.getMessage().hasContact()) {
                    handleContactMessage(update.getMessage().getContact().getPhoneNumber(), chatId);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing update for chatId {}: {}", chatId, e.getMessage(), e);
            messageSender.sendMessage(chatId, "An unexpected error occurred. Please try again.");
            userSessionService.clearSession(chatId);
        }
    }

    private void handleTextMessage(String messageText, Long chatId) { // Removed Update update parameter
        String currentState = userSessionService.getUserState(chatId);

        if (messageText.startsWith("/start")) {
            userSessionService.clearSession(chatId); // Always clear bot session on /start

            String command = messageText.substring("/start".length()).trim();

            if (command.startsWith("register_client_") || command.startsWith("register_driver_")) {
                String[] parts = command.split("_");
                if (parts.length == 3) {
                    String userTypeString = parts[1].toUpperCase();
                    String sessionId = parts[2];

                    try {
                        User.UserType userType = User.UserType.valueOf(userTypeString);
                        Optional<RegistrationCacheEntry> entryOpt = userSessionService.getRegistrationCacheEntry(sessionId);

                        if (entryOpt.isEmpty() || !entryOpt.get().getUserType().equals(userType)) {
                            messageSender.sendMessage(chatId, "This registration link is invalid or has expired. Please get a new link from the app.");
                            return;
                        }

                        // Start registration flow in bot service, which will set bot state and link sessionId
                        String response = telegramBotService.startRegistrationWithSessionId(sessionId, chatId);
                        messageSender.sendMessage(chatId, response);

                        // Always ask for phone number explicitly using the button
                        sendPhoneNumberRequest(chatId, "Please share your phone number to complete registration.");

                    } catch (IllegalArgumentException e) {
                        messageSender.sendMessage(chatId, "Invalid registration type in link. Please get a new link from the app.");
                    }
                } else {
                    messageSender.sendMessage(chatId, "Invalid registration link format. Please get a new link from the app.");
                }
            } else if (command.equals("forgot_password")) {
                userSessionService.setUserState(chatId, TelegramBotService.STATE_FORGOT_PASSWORD_AWAITING_PHONE);
                sendPhoneNumberRequest(chatId, "Please share your phone number to reset your password.");
            } else {
                messageSender.sendMessage(chatId, "Welcome! To register, please use the link provided by the app. To reset your password, use `/start forgot_password`.");
            }
        } else if (TelegramBotService.STATE_REGISTER_AWAITING_PHONE.equals(currentState) || TelegramBotService.STATE_FORGOT_PASSWORD_AWAITING_PHONE.equals(currentState)) {
            messageSender.sendMessage(chatId, "Please use the 'Share Phone Number' button below to provide your contact.");
            sendPhoneNumberRequest(chatId, "Still waiting for your phone number:"); // Resend keyboard
        } else {
            messageSender.sendMessage(chatId, "I don't understand that command. Please use the app's registration link or `/start forgot_password`.");
        }
    }

    private void handleContactMessage(String phoneNumber, Long chatId) {
        String currentState = userSessionService.getUserState(chatId);
        String responseMessage;

        String sessionId = userSessionService.getUserSession(chatId)
                .map(UserSession::getCurrentRegistrationSessionId)
                .orElse(null);

        if (TelegramBotService.STATE_REGISTER_AWAITING_PHONE.equals(currentState)) {
            if (sessionId != null) {
                responseMessage = telegramBotService.processPhoneNumberForSessionId(sessionId, phoneNumber);
                messageSender.removeReplyKeyboard(chatId, responseMessage);
                userSessionService.clearSession(chatId);
            } else {
                responseMessage = "Registration session not found. Please restart the process from the app's link.";
                messageSender.removeReplyKeyboard(chatId, responseMessage);
                userSessionService.clearSession(chatId);
            }
        } else if (TelegramBotService.STATE_FORGOT_PASSWORD_AWAITING_PHONE.equals(currentState)) {
            responseMessage = telegramBotService.resetPassword(chatId, phoneNumber);
            messageSender.removeReplyKeyboard(chatId, responseMessage);
            userSessionService.clearSession(chatId);
        } else {
            responseMessage = "I'm not expecting a phone number right now. Please use the app's registration link or `/start forgot_password`.";
            messageSender.removeReplyKeyboard(chatId, responseMessage);
            userSessionService.clearSession(chatId);
        }
    }

    private void sendPhoneNumberRequest(Long chatId, String promptText) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        KeyboardButton contactButton = new KeyboardButton();
        contactButton.setText("Share Phone Number");
        contactButton.setRequestContact(true);
        row.add(contactButton);
        rows.add(row);
        markup.setKeyboard(rows);

        messageSender.sendMessage(chatId, promptText, markup);
    }
}