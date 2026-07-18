package com.fitback.domain.consultation.dto;

import com.fitback.domain.consultation.enums.ConsultationMaterialType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConsultationMaterialFileData {

    private ConsultationMaterialType materialType;
    private String title;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String content;
}
