package com.fitback.domain.store.dto.request;

import com.fitback.domain.store.enums.StoreType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StoreSetupRequest {

    @NotBlank(message = "매장명을 입력하세요.")
    @Size(max = 100, message = "매장명은 100자 이하여야 합니다.")
    private String name;

    @NotNull(message = "업종을 선택하세요.")
    private StoreType storeType;

    private String phone;

    @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$", message = "사업자번호 형식이 올바르지 않습니다.")
    private String businessNumber;

    private String address;
}
