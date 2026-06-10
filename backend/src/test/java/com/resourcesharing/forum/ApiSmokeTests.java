package com.resourcesharing.forum;

import com.resourcesharing.forum.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void healthEndpointReturnsUnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UNCONFIGURED"));
    }

    @Test
    void loginEndpointReturnsToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"demo_user\",\"password\":\"123456\",\"rememberMe\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.user.username").value("demo_user"));
    }

    @Test
    void registerCodeEndpointValidatesEmailBeforeSending() throws Exception {
        clearInvocations(mailSender);

        mockMvc.perform(post("/api/v1/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void registerCodeEndpointSendsRealMailThroughMailSender() throws Exception {
        clearInvocations(mailSender);

        mockMvc.perform(post("/api/v1/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@qq.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.email").value("student@qq.com"))
                .andExpect(jsonPath("$.data.expiresInMinutes").value(10));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/user/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void writeEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"未登录资源\",\"description\":\"未登录不能发布资源\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"RESOURCE\",\"targetId\":1,\"content\":\"未登录评论\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"RESOURCE\",\"targetId\":1,\"reason\":\"未登录举报\"}"))
                .andExpect(status().isUnauthorized());

        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "content".getBytes());
        mockMvc.perform(multipart("/api/v1/attachments/upload").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void notificationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/notifications/1/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resourcesEndpointMatchesDesignPaginationContract() throws Exception {
        mockMvc.perform(get("/api/v1/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void loginTokenCanAccessDesignProfileEndpoint() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"demo_user\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = response.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/api/v1/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("demo_user"))
                .andExpect(jsonPath("$.data.points").exists())
                .andExpect(jsonPath("$.data.frozenPoints").exists())
                .andExpect(jsonPath("$.data.availablePoints").exists())
                .andExpect(jsonPath("$.data.rewardLimit").exists());
    }

    @Test
    void userProfileSummaryMatchesFrontendContract() throws Exception {
        String token = jwtService.generate("1", "MEMBER");
        mockMvc.perform(get("/api/me/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.profile.username").exists())
                .andExpect(jsonPath("$.data.resources").isArray())
                .andExpect(jsonPath("$.data.demands").isArray())
                .andExpect(jsonPath("$.data.favorites").isArray())
                .andExpect(jsonPath("$.data.likes").isArray())
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.loginLogs").isArray());
    }

    @Test
    void requestAndCommentPublicEndpointsMatchDesignContract() throws Exception {
        mockMvc.perform(get("/api/v1/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());

        mockMvc.perform(get("/api/demands/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demand").exists())
                .andExpect(jsonPath("$.data.replies").isArray())
                .andExpect(jsonPath("$.data.answers").isArray());

        mockMvc.perform(get("/api/v1/comments?targetType=RESOURCE&targetId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    void publicMetadataEndpointsAreAnonymousAndUnified() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/tags/suggest").param("keyword", "Java").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/resource-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    void userCenterStandardEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/user/resources"))
                .andExpect(status().isUnauthorized());

        String token = jwtService.generate("1", "MEMBER");
        mockMvc.perform(get("/api/v1/user/resources")
                        .param("page", "1")
                        .param("pageSize", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.size").value(5));

        mockMvc.perform(get("/api/v1/user/downloads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    void pointEndpointsExposeAccountAndPagedFlows() throws Exception {
        String token = jwtService.generate("1", "MEMBER");

        mockMvc.perform(get("/api/v1/user/points")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points").exists())
                .andExpect(jsonPath("$.data.frozenPoints").exists())
                .andExpect(jsonPath("$.data.levelInfo").exists())
                .andExpect(jsonPath("$.data.progressPercent").exists())
                .andExpect(jsonPath("$.data.rules").isArray());

        mockMvc.perform(get("/api/v1/user/points/flows")
                        .param("page", "2")
                        .param("pageSize", "7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(7))
                .andExpect(jsonPath("$.data.list").isArray());

        mockMvc.perform(get("/api/members/me/points")
                        .param("page", "2")
                        .param("size", "6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.points").exists())
                .andExpect(jsonPath("$.data.flows.page").value(2))
                .andExpect(jsonPath("$.data.flows.size").value(6));
    }

    @Test
    void interactionResponsesIncludeCommentLikeFieldsAndResourceDownloadFallback() throws Exception {
        String token = jwtService.generate("1", "MEMBER");

        mockMvc.perform(post("/api/v1/comments/1/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").exists())
                .andExpect(jsonPath("$.data.likeCount").exists());

        mockMvc.perform(post("/api/v1/resources/1/download")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/v1/attachments/1/stream"));
    }

    @Test
    void demandReplyEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/demands/1/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"这里是一条真实求资源回答\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCannotAuditResource() throws Exception {
        String token = jwtService.generate("1", "MEMBER");
        mockMvc.perform(put("/api/v1/resources/1/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDashboardEndpointsRequireAdminRoleAndUseUnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/admin/content"))
                .andExpect(status().isUnauthorized());

        String memberToken = jwtService.generate("1", "MEMBER");
        mockMvc.perform(get("/api/admin/content").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        String adminToken = jwtService.generate("2", "ADMIN");
        mockMvc.perform(get("/api/admin/content").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.auditRows").isArray())
                .andExpect(jsonPath("$.data.resourceRows").isArray())
                .andExpect(jsonPath("$.data.requestRows").isArray())
                .andExpect(jsonPath("$.data.commentRows").isArray());
    }

    @Test
    void adminDashboardSectionEndpointsUseStandardPagination() throws Exception {
        String adminToken = jwtService.generate("2", "ADMIN");

        mockMvc.perform(get("/api/admin/content")
                        .param("section", "audit")
                        .param("page", "0")
                        .param("size", "200")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.section").value("audit"))
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100));

        mockMvc.perform(get("/api/admin/content")
                        .param("section", "request")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section").value("request"))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));

        mockMvc.perform(get("/api/admin/compliance")
                        .param("section", "appeal")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section").value("appeal"))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));

        mockMvc.perform(get("/api/admin/catalog")
                        .param("section", "all")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.section").value("all"))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));

        mockMvc.perform(get("/api/admin/catalog/options")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.firstLevelCategories").isArray())
                .andExpect(jsonPath("$.data.secondLevelCategories").isArray())
                .andExpect(jsonPath("$.data.resourceTypes").isArray())
                .andExpect(jsonPath("$.data.tagCandidates").isArray())
                .andExpect(jsonPath("$.data.missingTags").isArray());
    }

    @Test
    void adminLogsDefaultToAuditDensity() throws Exception {
        String adminToken = jwtService.generate("2", "ADMIN");

        mockMvc.perform(get("/api/admin/logs")
                        .param("page", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(8));
    }

    @Test
    void adminCanAuditResource() throws Exception {
        String token = jwtService.generate("2", "ADMIN");
        mockMvc.perform(put("/api/v1/resources/1/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }
}
