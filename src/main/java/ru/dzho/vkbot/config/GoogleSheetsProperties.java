package ru.dzho.vkbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.sheets")
public record GoogleSheetsProperties(
        boolean enabled,
        String spreadsheetId,
        String sheetName,
        String credentialsPath,
        String applicationName,
        String apiBaseUrl
) {

    public String effectiveSheetName() {
        return isBlank(sheetName) ? "Голоса" : sheetName.trim();
    }

    public String effectiveApplicationName() {
        return isBlank(applicationName) ? "vk-comment-bot" : applicationName.trim();
    }

    public boolean hasSpreadsheetId() {
        return !isBlank(spreadsheetId);
    }

    public boolean hasCredentialsPath() {
        return !isBlank(credentialsPath);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
