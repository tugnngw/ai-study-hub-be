package com.tugnw.aistudy.domain.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "AI summary result")
public class SummaryResponse {

    @Schema(description = "Markdown-formatted summary")
    private String markdown;
}
