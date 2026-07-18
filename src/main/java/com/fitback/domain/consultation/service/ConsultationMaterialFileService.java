package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.dto.ConsultationMaterialFileData;
import com.fitback.domain.consultation.enums.ConsultationMaterialType;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ConsultationMaterialFileService {

    private static final int MAX_FILE_COUNT = 3;
    private static final long MAX_FILE_SIZE_BYTES = 1024L * 1024L;
    private static final int MAX_TOTAL_CONTENT_LENGTH = 30_000;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 255;
    private static final String TXT_EXTENSION = ".txt";
    private static final Charset MS949 = Charset.forName("MS949");

    public List<ConsultationMaterialFileData> extractMaterials(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new BusinessException(ConsultationErrorCode.CONSULTATION_MATERIAL_FILE_COUNT_EXCEEDED);
        }

        int totalContentLength = 0;
        List<ConsultationMaterialFileData> materials = files.stream()
                .map(this::extractMaterial)
                .toList();

        for (ConsultationMaterialFileData material : materials) {
            totalContentLength += material.getContent().length();
            if (totalContentLength > MAX_TOTAL_CONTENT_LENGTH) {
                throw new BusinessException(ConsultationErrorCode.CONSULTATION_MATERIAL_CONTENT_TOO_LONG);
            }
        }

        return materials;
    }

    private ConsultationMaterialFileData extractMaterial(MultipartFile file) {
        validateFile(file);

        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        String content = decodeContent(file).strip();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return ConsultationMaterialFileData.builder()
                .materialType(ConsultationMaterialType.OTHER)
                .title(resolveTitle(originalFileName))
                .originalFileName(truncate(originalFileName, MAX_ORIGINAL_FILE_NAME_LENGTH))
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .content(content)
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ConsultationErrorCode.CONSULTATION_MATERIAL_FILE_SIZE_EXCEEDED);
        }

        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        if (!StringUtils.hasText(originalFileName)
                || !originalFileName.toLowerCase().endsWith(TXT_EXTENSION)) {
            throw new BusinessException(ConsultationErrorCode.CONSULTATION_MATERIAL_UNSUPPORTED_FILE_TYPE);
        }
    }

    private String decodeContent(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            return decodeStrict(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ignored) {
            try {
                return decodeStrict(bytes, MS949);
            } catch (CharacterCodingException e) {
                throw new BusinessException(ConsultationErrorCode.CONSULTATION_MATERIAL_ENCODING_UNSUPPORTED);
            }
        }
    }

    private String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private String normalizeOriginalFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return "";
        }
        return StringUtils.cleanPath(originalFileName);
    }

    private String resolveTitle(String originalFileName) {
        String title = originalFileName;
        if (title.toLowerCase().endsWith(TXT_EXTENSION)) {
            title = title.substring(0, title.length() - TXT_EXTENSION.length());
        }
        if (!StringUtils.hasText(title)) {
            title = "consultation-material";
        }
        return truncate(title, MAX_TITLE_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
