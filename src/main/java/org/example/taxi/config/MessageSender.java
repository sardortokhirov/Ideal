package org.example.taxi.config; // Changed package to match your current project structure

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    private final UserSessionService sessionService;
    private AbsSender bot;

    public void setBot(AbsSender bot) {
        this.bot = bot;
    }

    public void sendMessage(SendMessage message, Long chatId) {
        try {
            message.setChatId(chatId);
            var sentMessage = bot.execute(message);
            // Ensure UserSession is fetched correctly from the new package
            UserSession session = sessionService.getUserSession(chatId).orElse(new UserSession());
            session.setChatId(chatId);
            List<Integer> messageIds = session.getMessageIds(); // Directly get from session
            messageIds.add(sentMessage.getMessageId());
            session.setMessageIds(messageIds);
            sessionService.saveUserSession(session);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendMessage(Long chatId, String text) {
        int maxLength = 4096;

        for (int start = 0; start < text.length(); start += maxLength) {
            int end = Math.min(start + maxLength, text.length());
            String chunk = text.substring(start, end);

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(chunk);
            sendMessage(message, chatId);
        }
    }

    public void sendMessage(Long chatId, String text, ReplyKeyboard replyMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(replyMarkup);
        sendMessage(message, chatId);
    }
    public void editMessageToRemoveButtons(Long chatId, Integer messageId) {
        EditMessageReplyMarkup editMessage = new EditMessageReplyMarkup();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setReplyMarkup(null);
        try {
            bot.execute(editMessage);
            logger.info("Removed buttons from message {} in chat {}", messageId, chatId);
        } catch (TelegramApiException e) {
            logger.error("Failed to remove buttons from message {} in chat {}: {}", messageId, chatId, e.getMessage());
        }
    }
    public void animateAndDeleteMessages(Long chatId, List<Integer> messageIds, String animationType) { // 'animationType' is unused here, keeping for consistency
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        for (Integer messageId : messageIds) {
            try {
                bot.execute(new DeleteMessage(String.valueOf(chatId), messageId));
            } catch (TelegramApiException e) {
                if (!e.getMessage().contains("message to delete not found")) { // Ignore 'message to delete not found' errors
                    logger.error("Error deleting message {} for chatId {}: {}", messageId, chatId, e.getMessage());
                }
            }
        }
        sessionService.clearMessageIds(chatId); // Clear message IDs after deleting
    }

    public void removeReplyKeyboard(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(new ReplyKeyboardRemove(true));
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error removing reply keyboard for chatId {}: {}", chatId, e.getMessage());
        }
    }
}