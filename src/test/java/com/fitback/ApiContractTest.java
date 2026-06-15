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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
@AutoConfigureMockMvc
class ApiContractTest {

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Autowired
    private MockMvc mockMvc;

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
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/store").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fitback Gym\",\"storeType\":\"GYM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fitback Gym"));

        String customer = mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kim\",\"phoneNum\":\"010-1234-5678\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(customer).contains("Kim");

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayConsultCount").isNumber());
    }
}
