package ru.dzho.vkbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.dzho.vkbot.config.GoogleSheetsProperties;
import ru.dzho.vkbot.model.db.ParticipantRecord;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleSheetsSyncService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsSyncService.class);
    private static final String SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");
    private static final List<String> HEADER = List.of(
            "VK User ID",
            "Имя / аккаунт",
            "Ссылка на профиль",
            "Пост",
            "Арт-стол",
            "Дата и время комментария",
            "Комментарий",
            "Номер подарка",
            "Подарок",
            "Статус подарка",
            "Дата выдачи подарка",
            "Ссылка в ЛС",
            "Режим ответа"
    );

    private final GoogleSheetsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile GoogleCredentials credentials;
    private volatile boolean configurationLogged;
    private final Set<String> readySheets = ConcurrentHashMap.newKeySet();

    public GoogleSheetsSyncService(GoogleSheetsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public boolean configured() {
        return properties.enabled() && properties.hasSpreadsheetId();
    }

    public synchronized void syncParticipant(ParticipantRecord participant) {
        if (!properties.enabled()) {
            return;
        }
        if (!isConfigured()) {
            return;
        }

        try {
            ensureSheetReady(properties.effectiveSheetName(), HEADER);
            List<Object> row = buildRow(participant);
            OptionalInt existingRow = findRowByUserId(properties.effectiveSheetName(), participant.userId());
            if (existingRow.isPresent()) {
                updateRow(properties.effectiveSheetName(), existingRow.getAsInt(), row, "M");
            } else {
                appendRow(properties.effectiveSheetName(), row, "M");
            }
        } catch (RuntimeException ex) {
            log.error("Google Sheets sync failed for user {}: {}", participant.userId(), ex.getMessage(), ex);
        }
    }

    private boolean isConfigured() {
        if (properties.hasSpreadsheetId()) {
            return true;
        }
        if (!configurationLogged) {
            configurationLogged = true;
            log.warn("Google Sheets sync is enabled, but GOOGLE_SHEETS_SPREADSHEET_ID is empty. Sync is skipped.");
        }
        return false;
    }

    private void ensureSheetReady(String sheetName, List<String> header) {
        if (readySheets.contains(sheetName)) {
            return;
        }

        JsonNode spreadsheet = requestJson("GET", spreadsheetMetadataPath(), null);
        boolean sheetExists = false;
        for (JsonNode sheet : spreadsheet.path("sheets")) {
            String title = sheet.path("properties").path("title").asText();
            if (sheetName.equals(title)) {
                sheetExists = true;
                break;
            }
        }

        if (!sheetExists) {
            requestJson("POST", spreadsheetBatchUpdatePath(), Map.of(
                    "requests", List.of(
                            Map.of("addSheet", Map.of("properties", Map.of("title", sheetName)))
                    )
            ));
        }

        ensureHeader(sheetName, header);
        readySheets.add(sheetName);
    }

    private void ensureHeader(String sheetName, List<String> header) {
        JsonNode current = requestJson("GET", valuesPath(range(sheetName, "1:1")), null);
        JsonNode values = current.path("values");
        if (values.isArray() && values.size() == 1 && headerMatches(values.get(0), header)) {
            return;
        }
        updateRange(range(sheetName, "A1:" + lastColumn(header.size()) + "1"), List.of(List.copyOf(header)));
    }

    private boolean headerMatches(JsonNode row, List<String> header) {
        if (!row.isArray() || row.size() != header.size()) {
            return false;
        }
        for (int index = 0; index < header.size(); index++) {
            if (!Objects.equals(header.get(index), row.get(index).asText())) {
                return false;
            }
        }
        return true;
    }

    private OptionalInt findRowByUserId(String sheetName, long userId) {
        JsonNode response = requestJson("GET", valuesPath(range(sheetName, "A2:A")), null);
        JsonNode values = response.path("values");
        if (!values.isArray()) {
            return OptionalInt.empty();
        }
        String expected = Long.toString(userId);
        for (int index = 0; index < values.size(); index++) {
            JsonNode row = values.get(index);
            if (row.isArray() && row.size() > 0 && expected.equals(row.get(0).asText())) {
                return OptionalInt.of(index + 2);
            }
        }
        return OptionalInt.empty();
    }

    private void appendRow(String sheetName, List<Object> row, String endColumn) {
        requestJson(
                "POST",
                valuesPath(range(sheetName, "A:" + endColumn)) + ":append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS",
                Map.of("values", List.of(row))
        );
    }

    private void updateRow(String sheetName, int rowIndex, List<Object> row, String endColumn) {
        updateRange(range(sheetName, "A" + rowIndex + ":" + endColumn + rowIndex), List.of(row));
    }

    private void updateRange(String a1Range, List<?> values) {
        requestJson(
                "PUT",
                valuesPath(a1Range) + "?valueInputOption=USER_ENTERED",
                Map.of("values", values)
        );
    }

    private List<Object> buildRow(ParticipantRecord participant) {
        return List.of(
                Long.toString(participant.userId()),
                formatUserName(participant),
                participant.profileUrl(),
                "https://vk.com/wall" + participant.sourceOwnerId() + "_" + participant.sourcePostId(),
                participant.artTable(),
                formatDateTime(participant.firstCommentAt()),
                nullToEmpty(participant.firstCommentText()),
                participant.giftNumber() == null ? "" : participant.giftNumber().toString(),
                nullToEmpty(participant.giftName()),
                humanGiftStatus(participant.giftStatus()),
                formatDateTime(participant.giftAssignedAt()),
                nullToEmpty(participant.replyLink()),
                nullToEmpty(participant.replyMode())
        );
    }

    private String formatUserName(ParticipantRecord participant) {
        String fullName = (nullToEmpty(participant.firstName()) + " " + nullToEmpty(participant.lastName())).trim();
        if (!nullToEmpty(participant.screenName()).isBlank()) {
            return fullName.isBlank()
                    ? "@" + participant.screenName()
                    : fullName + " (@" + participant.screenName() + ")";
        }
        return fullName;
    }

    private String humanGiftStatus(String giftStatus) {
        if (giftStatus == null || giftStatus.isBlank() || "NOT_ASSIGNED".equals(giftStatus)) {
            return "не выдан";
        }
        if ("ISSUED".equals(giftStatus)) {
            return "выдан";
        }
        return "не выдан";
    }

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private JsonNode requestJson(String method, String path, Object body) {
        URI uri = URI.create(normalizeBaseUrl(properties.apiBaseUrl()) + path);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header("User-Agent", properties.effectiveApplicationName());

        try {
            if ("GET".equals(method)) {
                requestBuilder.GET();
            } else {
                String payload = objectMapper.writeValueAsString(body);
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Google Sheets API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Google Sheets request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Sheets request interrupted", ex);
        }
    }

    private String accessToken() {
        try {
            GoogleCredentials current = credentials;
            if (current == null) {
                synchronized (this) {
                    current = credentials;
                    if (current == null) {
                        credentials = current = loadCredentials();
                    }
                }
            }

            current.refreshIfExpired();
            AccessToken accessToken = current.getAccessToken();
            if (accessToken == null || accessToken.getTokenValue() == null || accessToken.getTokenValue().isBlank()) {
                current.refresh();
                accessToken = current.getAccessToken();
            }
            if (accessToken == null || accessToken.getTokenValue() == null || accessToken.getTokenValue().isBlank()) {
                throw new IllegalStateException("Google credentials did not produce an access token");
            }
            return accessToken.getTokenValue();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to refresh Google access token", ex);
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        GoogleCredentials loaded;
        if (properties.hasCredentialsPath()) {
            try (InputStream inputStream = Files.newInputStream(Path.of(properties.credentialsPath()))) {
                loaded = GoogleCredentials.fromStream(inputStream);
            }
        } else {
            loaded = GoogleCredentials.getApplicationDefault();
        }
        return loaded.createScoped(SHEETS_SCOPE);
    }

    private String spreadsheetMetadataPath() {
        return "/v4/spreadsheets/" + urlEncode(properties.spreadsheetId()) + "?fields=sheets.properties.title";
    }

    private String spreadsheetBatchUpdatePath() {
        return "/v4/spreadsheets/" + urlEncode(properties.spreadsheetId()) + ":batchUpdate";
    }

    private String valuesPath(String a1Range) {
        return "/v4/spreadsheets/" + urlEncode(properties.spreadsheetId()) + "/values/" + urlEncode(a1Range);
    }

    private String range(String sheetName, String suffix) {
        String escapedSheetName = sheetName.replace("'", "''");
        return "'" + escapedSheetName + "'!" + suffix;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String normalizeBaseUrl(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String lastColumn(int count) {
        int value = count;
        StringBuilder builder = new StringBuilder();
        while (value > 0) {
            int remainder = (value - 1) % 26;
            builder.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return builder.toString();
    }
}
