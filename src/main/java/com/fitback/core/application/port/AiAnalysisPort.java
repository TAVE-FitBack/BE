package com.fitback.core.application.port;

import java.util.List;
import java.util.Map;

public interface AiAnalysisPort {
    Map<String, Object> analyze(String rawText);
    List<Map<String, Object>> generateMessages(Map<String, Object> context);
}
