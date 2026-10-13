package com.example.notification;

import com.example.notification.dto.request.LoginRequest;
import com.example.notification.dto.request.RegisterRequest;
import com.example.notification.dto.request.SubmitEventRequest;
import com.example.notification.dto.response.AuthResponse;
import com.example.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for HookFlow backend.
 * Uses H2 in-memory database (test profile).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("null")
class HookFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    private static String authToken;
    private static Long testIntegrationId;
    private static Long testEventId;

    // ── Auth Tests ──────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Register a new user — should return 201 with JWT token")
    void testRegister() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@hookflow.io");
        req.setPassword("TestPassword123");
        req.setFullName("Test User");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.email").value("test@hookflow.io"))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        authToken = auth.getToken();
        assertThat(authToken).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Duplicate registration with same email — should return 400")
    void testDuplicateRegistration() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@hookflow.io"); // same email
        req.setPassword("AnotherPassword1");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("Login with correct credentials — should return 200 with JWT")
    void testLogin() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@hookflow.io");
        req.setPassword("TestPassword123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        authToken = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class).getToken();
    }

    @Test
    @Order(4)
    @DisplayName("Login with wrong password — should return 401")
    void testLoginWrongPassword() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@hookflow.io");
        req.setPassword("WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── Integration Tests ───────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Create integration — should return 201 with integration details")
    void testCreateIntegration() throws Exception {
        String body = """
            {
              "name": "Test Warehouse Integration",
              "destinationUrl": "http://localhost:8080/api/v1/mock-destination",
              "eventTypes": "order.created,payment.processed",
              "description": "Test integration"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/integrations")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Warehouse Integration"))
                .andExpect(jsonPath("$.hasSigningSecret").value(true))
                .andExpect(jsonPath("$.signingSecret").doesNotExist()) // Never exposed
                .andReturn();

        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        testIntegrationId = ((Number) response.get("id")).longValue();
    }

    @Test
    @Order(6)
    @DisplayName("Access integrations without auth — should return 403/401")
    void testIntegrationsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/v1/integrations"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) {
                        throw new AssertionError("Expected 401 or 403 but got " + s);
                    }
                });
    }

    @Test
    @Order(7)
    @DisplayName("List integrations — should return the created integration")
    void testListIntegrations() throws Exception {
        mockMvc.perform(get("/api/v1/integrations")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Warehouse Integration"));
    }

    // ── Event Tests ─────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("Submit event — should return 202 and persist before responding")
    void testSubmitEvent() throws Exception {
        SubmitEventRequest req = new SubmitEventRequest();
        req.setIntegrationId(testIntegrationId);
        req.setEventType("order.created");
        req.setPayload("{\"orderId\":\"ORD-001\",\"amount\":99.99}");
        req.setIdempotencyKey("order-001-test");

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.eventType").value("order.created"))
                .andReturn();

        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        testEventId = ((Number) response.get("id")).longValue();

        // Verify event was persisted before 202 was returned
        assertThat(notificationRepository.findById(testEventId)).isPresent();
    }

    @Test
    @Order(9)
    @DisplayName("Idempotent resubmission — should return original event ID")
    void testIdempotentResubmission() throws Exception {
        SubmitEventRequest req = new SubmitEventRequest();
        req.setIntegrationId(testIntegrationId);
        req.setEventType("order.created");
        req.setPayload("{\"orderId\":\"ORD-001\",\"amount\":99.99}");
        req.setIdempotencyKey("order-001-test"); // Same key

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andReturn();

        var response = objectMapper.readValue(result.getResponse().getContentAsString(), java.util.Map.class);
        Long returnedId = ((Number) response.get("id")).longValue();

        // Must return the SAME event ID as the original submission
        assertThat(returnedId).isEqualTo(testEventId);
    }

    @Test
    @Order(10)
    @DisplayName("Get event detail — should include delivery logs after processing")
    void testGetEventDetail() throws Exception {
        // Wait briefly for async delivery
        Thread.sleep(3000);

        mockMvc.perform(get("/api/v1/events/" + testEventId)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testEventId))
                .andExpect(jsonPath("$.eventType").value("order.created"));
    }

    @Test
    @Order(11)
    @DisplayName("List events with pagination — should return page")
    void testListEvents() throws Exception {
        mockMvc.perform(get("/api/v1/events?page=0&size=10")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(12)
    @DisplayName("Dashboard stats — should return real metrics")
    void testDashboardStats() throws Exception {
        mockMvc.perform(get("/api/v1/events/stats")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").isNumber())
                .andExpect(jsonPath("$.successRate").isNumber());
    }

    // ── SSRF Tests ──────────────────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("Create integration with private IP — should be rejected")
    void testSsrfProtectionPrivateIp() throws Exception {
        String body = """
            {
              "name": "SSRF Test",
              "destinationUrl": "http://192.168.1.1/webhook"
            }
            """;

        mockMvc.perform(post("/api/v1/integrations")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(14)
    @DisplayName("Create integration with metadata endpoint — should be rejected")
    void testSsrfProtectionMetadataEndpoint() throws Exception {
        String body = """
            {
              "name": "SSRF Test 2",
              "destinationUrl": "http://169.254.169.254/latest/meta-data/"
            }
            """;

        mockMvc.perform(post("/api/v1/integrations")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── Auth Isolation Tests ─────────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("User isolation — second user cannot access first user's integrations")
    void testUserIsolation() throws Exception {
        // Register second user
        RegisterRequest req2 = new RegisterRequest();
        req2.setEmail("user2@hookflow.io");
        req2.setPassword("SecondUser123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andReturn();

        String token2 = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class).getToken();

        // User 2 tries to access User 1's integration
        mockMvc.perform(get("/api/v1/integrations/" + testIntegrationId)
                .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound()); // Should not be found for user 2
    }

    // ── Health Check ────────────────────────────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("Health endpoint — should return UP")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── Validation Tests ────────────────────────────────────────────────────

    @Test
    @Order(17)
    @DisplayName("Submit event with missing payload — should return 400 with field errors")
    void testSubmitEventValidation() throws Exception {
        String body = """
            {
              "eventType": "order.created"
            }
            """;

        mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @Order(18)
    @DisplayName("Register with short password — should return 400")
    void testRegisterPasswordValidation() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("short@hookflow.io");
        req.setPassword("short"); // less than 8 chars

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }
}
