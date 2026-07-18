package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.dto.ConsultationMaterialFileData;
import com.fitback.domain.consultation.enums.ConsultationMaterialType;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultationMaterialFileServiceTest {

    private final ConsultationMaterialFileService service = new ConsultationMaterialFileService();

    @Test
    @DisplayName("첨부파일이 없으면 빈 목록을 반환한다")
    void extractMaterialsEmpty() {
        assertThat(service.extractMaterials(null)).isEmpty();
        assertThat(service.extractMaterials(List.of())).isEmpty();
    }

    @Test
    @DisplayName("UTF-8 txt 파일을 읽고 기본 메타데이터를 생성한다")
    void extractUtf8TextMaterial() {
        MockMultipartFile file = file("kakao-chat.txt", "text/plain", " 상담 내용입니다. ");

        List<ConsultationMaterialFileData> materials = service.extractMaterials(List.of(file));

        assertThat(materials).hasSize(1);
        ConsultationMaterialFileData material = materials.get(0);
        assertThat(material.getMaterialType()).isEqualTo(ConsultationMaterialType.OTHER);
        assertThat(material.getTitle()).isEqualTo("kakao-chat");
        assertThat(material.getOriginalFileName()).isEqualTo("kakao-chat.txt");
        assertThat(material.getContentType()).isEqualTo("text/plain");
        assertThat(material.getFileSize()).isEqualTo(file.getSize());
        assertThat(material.getContent()).isEqualTo("상담 내용입니다.");
    }

    @Test
    @DisplayName("UTF-8 디코딩 실패 시 MS949로 다시 읽는다")
    void extractMs949TextMaterial() {
        byte[] bytes = "상담 내용입니다.".getBytes(Charset.forName("MS949"));
        MockMultipartFile file = new MockMultipartFile(
                "materials",
                "memo.txt",
                "text/plain",
                bytes
        );

        List<ConsultationMaterialFileData> materials = service.extractMaterials(List.of(file));

        assertThat(materials).hasSize(1);
        assertThat(materials.get(0).getContent()).isEqualTo("상담 내용입니다.");
    }

    @Test
    @DisplayName("첨부파일은 최대 3개까지만 허용한다")
    void extractMaterialsRejectsTooManyFiles() {
        List<MultipartFile> files = List.of(
                file("1.txt", "text/plain", "1"),
                file("2.txt", "text/plain", "2"),
                file("3.txt", "text/plain", "3"),
                file("4.txt", "text/plain", "4")
        );

        assertThatThrownBy(() -> service.extractMaterials(files))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.CONSULTATION_MATERIAL_FILE_COUNT_EXCEEDED);
    }

    @Test
    @DisplayName("파일당 1MB를 초과하면 예외가 발생한다")
    void extractMaterialsRejectsTooLargeFile() {
        byte[] bytes = new byte[1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("materials", "large.txt", "text/plain", bytes);

        assertThatThrownBy(() -> service.extractMaterials(List.of(file)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.CONSULTATION_MATERIAL_FILE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName(".txt가 아닌 파일은 허용하지 않는다")
    void extractMaterialsRejectsUnsupportedExtension() {
        MockMultipartFile file = file("memo.pdf", "application/pdf", "상담 내용");

        assertThatThrownBy(() -> service.extractMaterials(List.of(file)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.CONSULTATION_MATERIAL_UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("추출 텍스트가 비어 있으면 예외가 발생한다")
    void extractMaterialsRejectsBlankContent() {
        MockMultipartFile file = file("blank.txt", "text/plain", "   ");

        assertThatThrownBy(() -> service.extractMaterials(List.of(file)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("요청당 전체 추출 텍스트가 30000자를 초과하면 예외가 발생한다")
    void extractMaterialsRejectsTooLongTotalContent() {
        MockMultipartFile file1 = file("1.txt", "text/plain", "a".repeat(20_000));
        MockMultipartFile file2 = file("2.txt", "text/plain", "b".repeat(10_001));

        assertThatThrownBy(() -> service.extractMaterials(List.of(file1, file2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.CONSULTATION_MATERIAL_CONTENT_TOO_LONG);
    }

    private MockMultipartFile file(String originalFilename, String contentType, String content) {
        return new MockMultipartFile(
                "materials",
                originalFilename,
                contentType,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
