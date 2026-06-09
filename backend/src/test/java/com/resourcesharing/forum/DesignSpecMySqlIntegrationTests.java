package com.resourcesharing.forum;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "forum.upload.root-dir=target/test-uploads"
})
@AutoConfigureMockMvc
class DesignSpecMySqlIntegrationTests {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("resource_sharing_forum_it")
            .withUsername("forum")
            .withPassword("forum");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    DesignSpecMySqlIntegrationTests(MockMvc mockMvc, JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Test
    void registerCreatesAccountAndReturnsUnifiedResponse() throws Exception {
        String username = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = username + "@example.com";
        seedRegisterCode(email, "123456");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"abc123456","code":"123456"}
                                """.formatted(username, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.user.username").value(username));

        Integer linkedRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_account ua
                JOIN member_profile mp ON mp.account_id = ua.id
                JOIN member_point_account mpa ON mpa.member_id = mp.id
                JOIN point_flow pf ON pf.member_id = mp.id AND pf.scene = 'REGISTER'
                WHERE ua.username = ?
                """, Integer.class, username);
        org.assertj.core.api.Assertions.assertThat(linkedRows).isEqualTo(1);
    }

    @Test
    void loginFailureAccumulatesAndLocksAccount() throws Exception {
        String username = "lock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String email = username + "@example.com";
        seedRegisterCode(email, "123456");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"abc123456","code":"123456"}
                                """.formatted(username, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"account":"%s","password":"wrong-password"}
                                    """.formatted(username)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"abc123456"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("\u8d26\u53f7\u4e34\u65f6\u9501\u5b9a"));

        Integer failed = jdbcTemplate.queryForObject("SELECT failed_login_count FROM user_account WHERE username = ?", Integer.class, username);
        org.assertj.core.api.Assertions.assertThat(failed).isGreaterThanOrEqualTo(5);
    }

    @Test
    void memberCannotUseAdminAuditEndpoint() throws Exception {
        String token = loginToken("demo_user", "password");
        mockMvc.perform(put("/api/v1/resources/1/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAuditWritesNotificationAndUnreadCount() throws Exception {
        String adminToken = loginToken("admin", "password");
        mockMvc.perform(put("/api/v1/resources/2/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"reason\":\"integration approval\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        String memberToken = loginToken("user001", "password");
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count", greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void uploadRejectsExecutableFile() throws Exception {
        String token = loginToken("demo_user", "password");
        MockMultipartFile file = new MockMultipartFile("file", "run.exe", "application/x-msdownload", "bad".getBytes());
        mockMvc.perform(multipart("/api/v1/attachments/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void uploadAcceptsSafeFileAndDoesNotExposeStoragePath() throws Exception {
        String token = loginToken("demo_user", "password");
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/v1/attachments/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.fileName").value("note.txt"))
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.filePath").doesNotExist());
    }

    @Test
    void v5MigrationAddsIntegrityColumns() {
        Integer appealColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'appeal_record'
                  AND column_name = 'pending_target_key'
                """, Integer.class);
        Integer reportColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'report_complaint'
                  AND column_name = 'active_report_key'
                """, Integer.class);
        org.assertj.core.api.Assertions.assertThat(appealColumn).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(reportColumn).isEqualTo(1);
    }

    private String loginToken(String account, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"%s"}
                                """.formatted(account, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return response.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private void seedRegisterCode(String email, String code) {
        jdbcTemplate.update("""
                INSERT INTO email_verification_code(account_id, email, scene, code_hash, status, expire_time)
                VALUES (NULL, ?, 'REGISTER', ?, 'UNUSED', DATE_ADD(NOW(3), INTERVAL 10 MINUTE))
                """, email, passwordEncoder.encode(code));
    }
}
