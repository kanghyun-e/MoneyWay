package com.example.moneyway.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPlanClientTest {

    private final AiPlanClient aiPlanClient = new AiPlanClient("test-api-key");

    @Test
    void extractsJsonFromMarkdownCodeBlock() throws Exception {
        String content = """
                ```json
                {"totalUsedCost": 0, "days": []}
                ```
                """;

        String result = aiPlanClient.extractValidJsonContent(content);

        assertThat(result).isEqualTo("{\"totalUsedCost\": 0, \"days\": []}");
    }

    @Test
    void rejectsNonJsonContent() {
        assertThatThrownBy(() -> aiPlanClient.extractValidJsonContent("AI explanation without JSON"))
                .isInstanceOf(Exception.class);
    }
}
