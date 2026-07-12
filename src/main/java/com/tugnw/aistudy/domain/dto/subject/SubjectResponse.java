package com.tugnw.aistudy.domain.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Subject information")
public class SubjectResponse {

    @Schema(description = "Subject ID", example = "b2c3d4e5-...")
    private UUID id;

    @Schema(description = "Semester this subject belongs to", example = "a1b2c3d4-...")
    private UUID semesterId;

    @Schema(description = "Subject code", example = "SWP391")
    private String code;

    @Schema(description = "Subject name", example = "Software Development Project")
    private String name;

    @Schema(description = "Whether this is a default subject (General)", example = "false")
    private Boolean defaultSubject;
}
