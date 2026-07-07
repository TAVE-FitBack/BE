package com.fitback.domain.inquiry.dto.request;

import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class InquiryCustomerRequest {

    @NotBlank(message = "고객 이름은 필수입니다.")
    private String name;

    @NotNull(message = "성별은 필수입니다.")
    private Gender gender;

    @NotNull(message = "생년월일은 필수입니다.")
    private LocalDate birthDate;

    @NotBlank(message = "연락처는 필수입니다.")
    private String phoneNum;

    private PreferredContactChannel preferredContactChannel;

    private UUID inflowPathId;
}
