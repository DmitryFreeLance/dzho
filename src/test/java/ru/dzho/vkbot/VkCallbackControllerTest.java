package ru.dzho.vkbot;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VkCallbackControllerTest {

    private static final MockWebServer mockWebServer;

    static {
        try {
            mockWebServer = new MockWebServer();
            mockWebServer.start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterAll
    static void afterAll() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("vk.api-base-url", () -> mockWebServer.url("/").toString().replaceAll("/$", ""));
    }

    @Test
    void shouldReturnConfirmationCode() throws Exception {
        mockMvc.perform(post("/vk/callback")
                        .contentType("application/json")
                        .content("""
                                {
                                  "type": "confirmation",
                                  "group_id": 12345,
                                  "secret": "callback-secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("confirm-me"));
    }

    @Test
    void shouldAcceptFirstUserCommentAndReplyWithWriteLink() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": [
                            {
                              "id": 777,
                              "first_name": "Иван",
                              "last_name": "Иванов",
                              "screen_name": "ivan.test",
                              "photo_200": "https://example.com/avatar.jpg"
                            }
                          ]
                        }
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "comment_id": 9090
                          }
                        }
                        """));

        mockMvc.perform(post("/vk/callback")
                        .contentType("application/json")
                        .content("""
                                {
                                  "type": "wall_reply_new",
                                  "group_id": 12345,
                                  "secret": "callback-secret",
                                  "object": {
                                    "id": 5001,
                                    "from_id": 777,
                                    "owner_id": -12345,
                                    "post_id": 101,
                                    "reply_to_comment": null,
                                    "date": 1719900000,
                                    "text": "Голосую за этот стол"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        RecordedRequest usersGet = mockWebServer.takeRequest();
        assertThat(usersGet.getPath()).isEqualTo("/method/users.get");
        String usersGetBody = usersGet.getBody().readUtf8();
        assertThat(usersGetBody).contains("user_ids=777");
        assertThat(usersGetBody).contains("fields=screen_name%2Cphoto_200");

        RecordedRequest createComment = mockWebServer.takeRequest();
        assertThat(createComment.getPath()).isEqualTo("/method/wall.createComment");
        String wallBody = createComment.getBody().readUtf8();
        assertThat(wallBody).contains("reply_to_comment=5001");
        assertThat(wallBody).contains("from_group=12345");
        assertThat(wallBody).contains("vk.com%2Fwrite-236069574%3Fref%3Dpost_101_comment_5001");

        Integer participantCount = jdbcClient.sql("SELECT COUNT(*) FROM participants WHERE user_id = 777")
                .query(Integer.class)
                .single();
        assertThat(participantCount).isEqualTo(1);

        String eventStatus = jdbcClient.sql("SELECT status FROM comment_events WHERE comment_id = 5001")
                .query(String.class)
                .single();
        assertThat(eventStatus).isEqualTo("ACCEPTED");
    }

    @Test
    void shouldNotReplyAgainForDuplicateUser() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": [
                            {
                              "id": 888,
                              "first_name": "Анна",
                              "last_name": "Петрова",
                              "screen_name": "anna.test",
                              "photo_200": "https://example.com/anna.jpg"
                            }
                          ]
                        }
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "comment_id": 9191
                          }
                        }
                        """));

        mockMvc.perform(post("/vk/callback")
                        .contentType("application/json")
                        .content("""
                                {
                                  "type": "wall_reply_new",
                                  "group_id": 12345,
                                  "secret": "callback-secret",
                                  "object": {
                                    "id": 6001,
                                    "from_id": 888,
                                    "owner_id": -12345,
                                    "post_id": 101,
                                    "date": 1719900000,
                                    "text": "Первый комментарий"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockWebServer.takeRequest();
        mockWebServer.takeRequest();

        mockMvc.perform(post("/vk/callback")
                        .contentType("application/json")
                        .content("""
                                {
                                  "type": "wall_reply_new",
                                  "group_id": 12345,
                                  "secret": "callback-secret",
                                  "object": {
                                    "id": 6002,
                                    "from_id": 888,
                                    "owner_id": -12345,
                                    "post_id": 101,
                                    "date": 1719900100,
                                    "text": "Повторный комментарий"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

        Integer duplicateCount = jdbcClient.sql("SELECT duplicate_comments_count FROM participants WHERE user_id = 888")
                .query(Integer.class)
                .single();
        assertThat(duplicateCount).isEqualTo(1);

        String duplicateStatus = jdbcClient.sql("SELECT status FROM comment_events WHERE comment_id = 6002")
                .query(String.class)
                .single();
        assertThat(duplicateStatus).isEqualTo("DUPLICATE_USER");
    }

    @Test
    void shouldFallbackWhenVkRejectsHyperlinks() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": [
                            {
                              "id": 999,
                              "first_name": "Олег",
                              "last_name": "Сидоров",
                              "screen_name": "oleg.test",
                              "photo_200": "https://example.com/oleg.jpg"
                            }
                          ]
                        }
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "error": {
                            "error_code": 222,
                            "error_msg": "Hyperlinks are forbidden"
                          }
                        }
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "response": {
                            "comment_id": 9292
                          }
                        }
                        """));

        mockMvc.perform(post("/vk/callback")
                        .contentType("application/json")
                        .content("""
                                {
                                  "type": "wall_reply_new",
                                  "group_id": 12345,
                                  "secret": "callback-secret",
                                  "object": {
                                    "id": 7001,
                                    "from_id": 999,
                                    "owner_id": -12345,
                                    "post_id": 101,
                                    "date": 1719900000,
                                    "text": "Хочу участвовать"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockWebServer.takeRequest();
        mockWebServer.takeRequest();
        RecordedRequest fallbackRequest = mockWebServer.takeRequest();
        String fallbackBody = fallbackRequest.getBody().readUtf8();
        assertThat(fallbackBody).contains("%5Bclub236069574%7C%D1%81%D0%BE%D0%BE%D0%B1%D1%89%D0%B5%D0%BD%D0%B8%D0%B5+%D1%81%D0%BE%D0%BE%D0%B1%D1%89%D0%B5%D1%81%D1%82%D0%B2%D0%B0%5D");

        String replyMode = jdbcClient.sql("SELECT reply_mode FROM participants WHERE user_id = 999")
                .query(String.class)
                .single();
        assertThat(replyMode).isEqualTo("FALLBACK_NO_LINK");
    }
}
