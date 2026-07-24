package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.semester.SemesterResponse;
import com.tugnw.aistudy.service.SemesterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/semesters")
@Tag(name = "Semesters", description = "Academic semester management")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    @GetMapping
    @Operation(summary = "List all semesters", description = "Returns all academic semesters ordered by name")
    public ApiResponse<List<SemesterResponse>> getAllSemesters() {
        return ApiResponse.success(semesterService.getAllSemesters());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get semester by ID")
    public ApiResponse<SemesterResponse> getSemesterById(@PathVariable UUID id) {
        return ApiResponse.success(semesterService.getSemesterById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a semester (admin only)")
    public ApiResponse<SemesterResponse> createSemester(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return ApiResponse.success(semesterService.createSemester(name));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a semester (admin only)")
    public ApiResponse<Void> deleteSemester(@PathVariable UUID id) {
        semesterService.deleteSemester(id);
        return ApiResponse.success(null);
    }
}
