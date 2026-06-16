package ru.dzho.vkbot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import ru.dzho.vkbot.config.VkBotProperties;
import ru.dzho.vkbot.model.vk.VkUserProfile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class VkApiClient {

    private final VkBotProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public VkApiClient(VkBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public VkUserProfile getUserProfile(long userId) {
        JsonNode response = call("users.get", Map.of(
                "user_ids", Long.toString(userId),
                "fields", "screen_name,photo_200"
        ));

        JsonNode user = response.path(0);
        if (user.isMissingNode() || user.isNull()) {
            throw new VkApiException(-1, "VK users.get returned empty response");
        }

        String screenName = textOrNull(user, "screen_name");
        String profileUrl = (screenName != null && !screenName.isBlank())
                ? "https://vk.com/" + screenName
                : "https://vk.com/id" + user.path("id").asLong(userId);

        return new VkUserProfile(
                user.path("id").asLong(userId),
                textOrNull(user, "first_name"),
                textOrNull(user, "last_name"),
                screenName,
                textOrNull(user, "photo_200"),
                profileUrl
        );
    }

    public Long createCommentReply(long ownerId, long postId, long replyToComment, long fromGroupId, String message) {
        JsonNode response = call("wall.createComment", Map.of(
                "owner_id", Long.toString(ownerId),
                "post_id", Long.toString(postId),
                "reply_to_comment", Long.toString(replyToComment),
                "from_group", Long.toString(fromGroupId),
                "message", message
        ));

        if (response.isIntegralNumber()) {
            return response.asLong();
        }
        if (response.hasNonNull("comment_id")) {
            return response.get("comment_id").asLong();
        }
        return null;
    }

    private JsonNode call(String method, Map<String, String> methodParams) {
        Map<String, String> params = new LinkedHashMap<>(methodParams);
        params.put("access_token", properties.accessToken());
        params.put("v", properties.apiVersion());

        String formBody = formEncode(params);
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.apiBaseUrl())
                .pathSegment("method", method)
                .build(true)
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                JsonNode error = root.get("error");
                throw new VkApiException(error.path("error_code").asInt(-1), error.path("error_msg").asText("VK API error"));
            }
            return root.path("response");
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException("VK API timeout for method " + method, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("VK API request failed for method " + method, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("VK API request failed for method " + method, ex);
        }
    }

    private String formEncode(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(encode(key) + "=" + encode(value)));
        return joiner.toString();
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
