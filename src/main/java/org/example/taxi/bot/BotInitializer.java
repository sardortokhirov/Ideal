package org.example.taxi.bot;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Date-9/18/2025
 * By Sardor Tokhirov
 * Time-3:24 PM (GMT+5)
 */

@Configuration
public class BotInitializer {

    @Autowired
    private TelegramBot telegramBot;

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBot); // ✅ Register second bot here
            System.out.println("✅ Both bots started and registered successfully!");
        } catch (TelegramApiException e) {
            System.out.println("❌ Failed to register bots: " + e.getMessage());
        }
    }
}