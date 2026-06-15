package com.fitback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "jwt.secret=local-development-secret-key-change-me",
        "ai.fallback-enabled=true"
})
@AutoConfigureMockMvc
class ApiContractTest {

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mapsEverySpecifiedApiRoute() {
        Set<String> actual = mappings.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream()
                        .flatMap(path -> info.getMethodsCondition().getMethods().stream()
                                .map(method -> method + " " + path.replaceAll("\\{[^/]+}", "{}"))))
                .collect(Collectors.toSet());

        Set<String> expected = Set.of(
                "POST /api/v1/auth/login", "POST /api/v1/auth/register", "POST /api/v1/auth/refresh",
                "POST /api/v1/auth/logout", "POST /api/v1/auth/password-reset/request",
                "POST /api/v1/auth/password-reset/confirm", "GET /api/v1/store", "POST /api/v1/store",
                "PUT /api/v1/store", "GET /api/v1/store/services", "POST /api/v1/store/services",
                "PUT /api/v1/store/services/{serviceId}", "GET /api/v1/store/settings",
                "PUT /api/v1/store/settings", "GET /api/v1/customers", "POST /api/v1/customers",
                "GET /api/v1/customers/{customerId}", "PUT /api/v1/customers/{customerId}",
                "DELETE /api/v1/customers/{customerId}", "GET /api/v1/customers/{customerId}/consultations",
                "POST /api/v1/customers/{customerId}/consultations",
                "GET /api/v1/consultations/{consultationId}",
                "POST /api/v1/consultations/{consultationId}/analyze",
                "PUT /api/v1/consultations/{consultationId}",
                "GET /api/v1/customers/{customerId}/interest-services",
                "PUT /api/v1/customers/{customerId}/interest-services",
                "GET /api/v1/consultations/{consultationId}/reasons",
                "PUT /api/v1/consultations/{consultationId}/reasons", "GET /api/v1/follow-ups",
                "GET /api/v1/customers/{customerId}/follow-ups", "PATCH /api/v1/follow-ups/{followUpId}/status",
                "PATCH /api/v1/follow-ups/{followUpId}/snooze", "PUT /api/v1/follow-ups/{followUpId}",
                "POST /api/v1/customers/{customerId}/contact-results",
                "GET /api/v1/customers/{customerId}/contact-results",
                "POST /api/v1/follow-ups/{followUpId}/messages/generate",
                "GET /api/v1/follow-ups/{followUpId}/messages", "PUT /api/v1/messages/{messageId}",
                "PATCH /api/v1/messages/{messageId}/copy", "POST /api/v1/messages/{messageId}/send",
                "POST /api/v1/messages/delivery-callback", "GET /api/v1/store/events", "POST /api/v1/store/events",
                "GET /api/v1/events/{eventId}/targets", "POST /api/v1/event-targets/{targetId}/send",
                "PATCH /api/v1/event-targets/{targetId}/status",
                "POST /api/v1/customers/{customerId}/enrollments",
                "GET /api/v1/customers/{customerId}/enrollments",
                "PATCH /api/v1/enrollments/{enrollmentId}/status", "GET /api/v1/dashboard/summary",
                "GET /api/v1/dashboard/priority-customers", "GET /api/v1/reports/conversion",
                "GET /api/v1/reports/non-conversion-reasons", "GET /api/v1/reports/follow-up-funnel",
                "GET /api/v1/reports/consultation").stream()
                .map(route -> route.replaceAll("\\{[^/]+}", "{}"))
                .collect(Collectors.toSet());
        assertThat(actual).containsAll(expected);
    }

    @Test
    void supportsCoreCustomerWorkflow() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@fitback.test\",\"password\":\"secret12\",\"name\":\"Owner\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@fitback.test\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/store").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fitback Gym\",\"storeType\":\"GYM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fitback Gym"));

        String customer = mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kim\",\"phoneNum\":\"010-1234-5678\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(customer).contains("Kim");

        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayConsultCount").isNumber());
    }

    @Test
    void rejectsProtectedRouteWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"forged\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesRefreshTokenAndRejectsReuse() throws Exception {
        String email = "rotate@fitback.test";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"secret12\",\"name\":\"Owner\"}"));
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret12\"}"))
                .andReturn();
        String refresh = objectMapper.readTree(login.getResponse().getContentAsString()).get("refreshToken").asText();
        String body = "{\"refreshToken\":\"" + refresh + "\"}";

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnsignedDeliveryCallback() throws Exception {
        mockMvc.perform(post("/api/v1/messages/delivery-callback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"00000000-0000-0000-0000-000000000000\",\"status\":\"DELIVERED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void isolatesCustomerDataByJwtStoreId() throws Exception {
        String first = registerAndLogin("first@fitback.test");
        String second = registerAndLogin("second@fitback.test");
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Tenant One Customer\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"secret12\",\"name\":\"Owner\"}"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
