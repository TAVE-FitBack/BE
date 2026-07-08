package com.fitback.domain.consultation.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiNextActionRegenerateResponse {

    private Integer priorityScore;
    private NextBestAction nextBestAction;
    private FollowUp followUp;
    private FollowUpInsight followUpInsight;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NextBestAction {
        private String title;
        private String description;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FollowUp {
        private LocalDate recommendContactDate;
        private String memo;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FollowUpInsight {
        private Map<String, Object> persuasionPoint;
        private String cautionNote;
        private Map<String, Object> actionBasis;
    }
}
