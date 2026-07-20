package com.fitback.domain.customer.dto.request;

import com.fitback.domain.customer.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class CustomerInfoUpdateRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotNull(message = "생년월일은 필수입니다.")
    private LocalDate birthDate;

    @NotNull(message = "성별은 필수입니다.")
    @Schema(description = "Customer gender", allowableValues = {"MALE", "FEMALE"}, example = "MALE")
    private Gender gender;

    @NotBlank(message = "연락처는 필수입니다.")
    private String phoneNum;

    @NotNull(message = "방문 시간은 필수입니다.")
    @Schema(description = "최근 상담(회차)의 방문 일시", example = "2026-07-22T01:30:00+09:00")
    private OffsetDateTime visitAt;
}
