package com.tugnw.aistudy.domain.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Subject information")
public class SubjectResponse {

    @Schema(description = "Subject ID", example = "1")
    private Long id;

    @Schema(description = "Semester this subject belongs to", example = "1")
    private Long semesterId;

    @Schema(description = "Subject code", example = "SWP391")
    private String code;

    @Schema(description = "Subject name", example = "Software Development Project")
    private String name;
}
