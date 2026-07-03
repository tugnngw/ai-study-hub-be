package com.tugnw.aistudy.domain.dto.ai;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SummaryRequest {

    @NotEmpty(message = "documentIds must not be empty")
    private List<UUID> documentIds;

    private boolean force;
}
