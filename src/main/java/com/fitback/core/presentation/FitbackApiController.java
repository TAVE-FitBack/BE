package com.fitback.core.presentation;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fitback.core.application.FitbackApplicationService;
import com.fitback.core.application.DeliveryCallbackService;

@RestController
@RequestMapping("/api/v1")
public class FitbackApiController {
    private final FitbackApplicationService app;
    private final DeliveryCallbackService callbacks;

    public FitbackApiController(FitbackApplicationService app, DeliveryCallbackService callbacks) {
        this.app = app;
        this.callbacks = callbacks;
    }

    @PostMapping("/auth/register") ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> b) { return created(app.register(b)); }
    @PostMapping("/auth/login") Map<String, Object> login(@RequestBody Map<String, Object> b) { return app.login(b); }
    @PostMapping("/auth/refresh") Map<String, Object> refresh(@RequestBody Map<String, Object> b) { return app.refresh(String.valueOf(b.get("refreshToken"))); }
    @PostMapping("/auth/logout") ResponseEntity<Void> logout(@RequestBody(required=false) Map<String, Object> b) { app.logout(b == null ? null : String.valueOf(b.get("refreshToken"))); return ResponseEntity.noContent().build(); }
    @PostMapping("/auth/password-reset/request") Map<String, Object> resetRequest(@RequestBody Map<String, Object> b) { return app.requestPasswordReset(String.valueOf(b.get("email"))); }
    @PostMapping("/auth/password-reset/confirm") Map<String, Object> resetConfirm(@RequestBody Map<String, Object> b) { return app.confirmPasswordReset(String.valueOf(b.get("token")), String.valueOf(b.get("newPassword"))); }

    @GetMapping("/store") Map<String, Object> store() { return app.singleton("store"); }
    @PostMapping("/store") ResponseEntity<Map<String, Object>> createStore(@RequestBody Map<String, Object> b) { return created(app.saveSingleton("store", b)); }
    @PutMapping("/store") Map<String, Object> updateStore(@RequestBody Map<String, Object> b) { return app.saveSingleton("store", b); }
    @GetMapping("/store/services") List<Map<String, Object>> services() { return app.list("services"); }
    @PostMapping("/store/services") ResponseEntity<Map<String, Object>> createService(@RequestBody Map<String, Object> b) { return created(app.create("services", b)); }
    @PutMapping("/store/services/{id}") Map<String, Object> updateService(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.update("services", id, b); }
    @GetMapping("/store/settings") Map<String, Object> settings() { return app.singleton("settings"); }
    @PutMapping("/store/settings") Map<String, Object> updateSettings(@RequestBody Map<String, Object> b) { return app.saveSingleton("settings", b); }

    @GetMapping("/customers") Map<String, Object> customers(
            @RequestParam(required=false) String ids,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) String temperature,
            @RequestParam(required=false) String reasonType,
            @RequestParam(required=false) String search,
            @RequestParam(required=false) String sortBy,
            @RequestParam(defaultValue="desc") String order,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        if (ids != null && !ids.isBlank()) {
            return app.customersByIds(ids);
        }
        return app.searchCustomers(status, temperature, reasonType, search, sortBy, order, page, Math.max(1, Math.min(size, 100)));
    }
    @PostMapping("/customers") ResponseEntity<Map<String, Object>> createCustomer(@RequestBody Map<String, Object> b) { Map<String, Object> c = app.create("customers", b); c.put("isDuplicate", false); return created(c); }
    @GetMapping("/customers/{id}") Map<String, Object> customer(@PathVariable String id) { return app.customerDetail(id); }
    @PutMapping("/customers/{id}") Map<String, Object> updateCustomer(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.update("customers", id, b); }
    @DeleteMapping("/customers/{id}") ResponseEntity<Void> deleteCustomer(@PathVariable String id) { app.deleteCustomer(id); return ResponseEntity.noContent().build(); }
    @GetMapping("/customers/{id}/consultations") List<Map<String, Object>> consultations(@PathVariable String id) { return app.by("consultations", "customerId", id); }
    @PostMapping("/customers/{id}/consultations") ResponseEntity<Map<String, Object>> createConsultation(@PathVariable String id, @RequestBody Map<String, Object> b) { return created(app.createConsultation(id, b)); }
    @GetMapping("/consultations/check-duplicate") Map<String, Object> checkConsultationDuplicate(
            @RequestParam(required=false) String phoneNum,
            @RequestParam(required=false) String phoneNumber,
            @RequestParam(required=false) String name) {
        return app.checkConsultationDuplicate(phoneNum == null ? phoneNumber : phoneNum, name);
    }
    @PostMapping("/consultations/analyze-preview") Map<String, Object> analyzeConsultationPreview(@RequestBody Map<String, Object> b) { return app.analyzeConsultationPreview(b); }
    @PostMapping("/consultations") ResponseEntity<Map<String, Object>> createConsultationRecord(@RequestBody Map<String, Object> b) { return created(app.createConsultationRecord(b)); }
    @GetMapping("/consultations/{id}") Map<String, Object> consultation(@PathVariable String id) { return app.get("consultations", id); }
    @PostMapping("/consultations/{id}/analyze") Map<String, Object> analyze(@PathVariable String id) { return app.analyzeConsultation(id); }
    @PutMapping("/consultations/{id}") Map<String, Object> updateConsultation(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.update("consultations", id, b); }
    @GetMapping("/customers/{id}/interest-services") List<Map<String, Object>> interests(@PathVariable String id) { return app.interests(id); }
    @PutMapping("/customers/{id}/interest-services") List<Map<String, Object>> replaceInterests(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.replaceInterests(id, b); }
    @GetMapping("/consultations/{id}/reasons") List<Map<String, Object>> reasons(@PathVariable String id) { return app.by("reasons", "consultationId", id); }
    @PutMapping("/consultations/{id}/reasons") List<Map<String, Object>> replaceReasons(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.replaceReasons(id, b); }

    @GetMapping("/follow-ups") List<Map<String, Object>> followUps() { return app.list("followUps"); }
    @GetMapping("/customers/{id}/follow-ups") List<Map<String, Object>> customerFollowUps(@PathVariable String id) { return app.by("followUps", "customerId", id); }
    @PatchMapping("/follow-ups/{id}/status") Map<String, Object> followUpStatus(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.update("followUps", id, b); }
    @PatchMapping("/follow-ups/{id}/snooze") Map<String, Object> snooze(@PathVariable String id, @RequestBody(required=false) Map<String, Object> b) { return app.update("followUps", id, b == null ? Map.of("status", "SNOOZED") : b); }
    @PutMapping("/follow-ups/{id}") Map<String, Object> updateFollowUp(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.update("followUps", id, b); }
    @PostMapping("/customers/{id}/contact-results") ResponseEntity<Map<String, Object>> contactResult(@PathVariable String id, @RequestBody Map<String, Object> b) { return created(app.createContactResult(id, b)); }
    @GetMapping("/customers/{id}/contact-results") List<Map<String, Object>> contactResults(@PathVariable String id) { return app.by("contactResults", "customerId", id); }

    @PostMapping("/follow-ups/{id}/messages/generate") ResponseEntity<List<Map<String, Object>>> generateMessages(@PathVariable String id) { return ResponseEntity.status(HttpStatus.CREATED).body(app.generateMessages(id)); }
    @GetMapping("/follow-ups/{id}/messages") List<Map<String, Object>> messages(@PathVariable String id) { return app.by("messages", "followUpId", id); }
    @PutMapping("/messages/{id}") Map<String, Object> updateMessage(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.update("messages", id, b); }
    @PatchMapping("/messages/{id}/copy") Map<String, Object> copyMessage(@PathVariable String id) { return app.update("messages", id, Map.of("deliveryStatus", "COPIED", "sentAt", java.time.Instant.now().toString())); }
    @PostMapping("/messages/{id}/send") ResponseEntity<Map<String, Object>> sendMessage(@PathVariable String id, @RequestBody(required=false) Map<String, Object> b) { return ResponseEntity.accepted().body(app.sendMessage(id, b)); }
    @PostMapping("/messages/delivery-callback") Map<String, Object> callback(
            @RequestHeader(value="X-Webhook-Signature", required=false) String signature,
            @RequestBody Map<String, Object> b) { return callbacks.apply(signature, b); }

    @GetMapping("/store/events") List<Map<String, Object>> events() { return app.list("events"); }
    @PostMapping("/store/events") ResponseEntity<Map<String, Object>> createEvent(@RequestBody Map<String, Object> b) { return created(app.createEvent(b)); }
    @GetMapping("/events/{id}/targets") List<Map<String, Object>> targets(@PathVariable String id) { return app.by("eventTargets", "eventId", id); }
    @PostMapping("/event-targets/{id}/send") ResponseEntity<Map<String, Object>> sendTarget(@PathVariable String id, @RequestBody(required=false) Map<String, Object> b) { return ResponseEntity.accepted().body(app.update("eventTargets", id, Map.of("status", "SENT"))); }
    @PatchMapping("/event-targets/{id}/status") Map<String, Object> targetStatus(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.update("eventTargets", id, b); }
    @PostMapping("/customers/{id}/enrollments") ResponseEntity<Map<String, Object>> enroll(@PathVariable String id, @RequestBody Map<String, Object> b) { return created(app.enroll(id, b)); }
    @GetMapping("/customers/{id}/enrollments") List<Map<String, Object>> enrollments(@PathVariable String id) { return app.by("enrollments", "customerId", id); }
    @PatchMapping("/enrollments/{id}/status") Map<String, Object> enrollmentStatus(@PathVariable String id, @RequestBody Map<String, Object> b) { return app.update("enrollments", id, b); }

    @GetMapping("/dashboard/summary") Map<String, Object> summary() { return app.summary(); }
    @GetMapping("/dashboard/priority-customers") List<Map<String, Object>> priorityCustomers() { return app.priorityCustomers(); }
    @GetMapping("/reports/conversion") Object conversion() { return app.report("conversion"); }
    @GetMapping("/reports/non-conversion-reasons") Object nonConversionReasons() { return app.report("non-conversion-reasons"); }
    @GetMapping("/reports/follow-up-funnel") Object followUpFunnel() { return app.report("follow-up-funnel"); }
    @GetMapping("/reports/consultation") Object consultationReport() { return app.report("consultation"); }

    private ResponseEntity<Map<String, Object>> created(Map<String, Object> body) { return ResponseEntity.status(HttpStatus.CREATED).body(body); }
}
