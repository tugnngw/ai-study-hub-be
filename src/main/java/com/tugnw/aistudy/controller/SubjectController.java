package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.subject.SubjectResponse;
import com.tugnw.aistudy.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subjects")
@Tag(name = "Subjects", description = "Academic subject management")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping("/semester/{semesterId}")
    @Operation(summary = "List subjects by semester")
    public ResponseEntity<List<SubjectResponse>> getSubjectsBySemester(@PathVariable Long semesterId) {
        return ResponseEntity.ok(subjectService.getSubjectsBySemester(semesterId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subject by ID")
    public ResponseEntity<SubjectResponse> getSubjectById(@PathVariable Long id) {
        return ResponseEntity.ok(subjectService.getSubjectById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a subject (admin only)")
    public ResponseEntity<SubjectResponse> createSubject(@RequestBody Map<String, Object> body) {
        Long semesterId = Long.valueOf(body.get("semesterId").toString());
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subjectService.createSubject(semesterId, code, name));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a subject (admin only)")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }
}
