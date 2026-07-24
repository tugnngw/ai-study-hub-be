package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.subject.SubjectResponse;
import com.tugnw.aistudy.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/subjects")
@Tag(name = "Subjects", description = "Academic subject management")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping("/semester/{semesterId}")
    @Operation(summary = "List subjects by semester")
    public ApiResponse<List<SubjectResponse>> getSubjectsBySemester(@PathVariable UUID semesterId) {
        return ApiResponse.success(subjectService.getSubjectsBySemester(semesterId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subject by ID")
    public ApiResponse<SubjectResponse> getSubjectById(@PathVariable UUID id) {
        return ApiResponse.success(subjectService.getSubjectById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a subject (admin only)")
    public ApiResponse<SubjectResponse> createSubject(@RequestBody Map<String, Object> body) {
        UUID semesterId = UUID.fromString(body.get("semesterId").toString());
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        return ApiResponse.success(subjectService.createSubject(semesterId, code, name));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a subject (admin only)")
    public ApiResponse<Void> deleteSubject(@PathVariable UUID id) {
        subjectService.deleteSubject(id);
        return ApiResponse.success("Subject deleted successfully", null);
    }
}
