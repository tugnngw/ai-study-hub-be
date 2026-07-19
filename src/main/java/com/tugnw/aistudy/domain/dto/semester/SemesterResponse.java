package com.tugnw.aistudy.domain.dto.semester;

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
@Schema(description = "Semester information")
public class SemesterResponse {

    @Schema(description = "Semester ID", example = "a1b2c3d4-...")
    private UUID id;

    @Schema(description = "Semester name", example = "Spring 2025")
    private String name;

}
