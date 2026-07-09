package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.semester.SemesterResponse;
import com.tugnw.aistudy.service.SemesterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/semesters")
@Tag(name = "Semesters", description = "Academic semester management")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    @GetMapping
    @Operation(summary = "List all semesters", description = "Returns all academic semesters ordered by name")
    public ResponseEntity<List<SemesterResponse>> getAllSemesters() {
        return ResponseEntity.ok(semesterService.getAllSemesters());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get semester by ID")
    public ResponseEntity<SemesterResponse> getSemesterById(@PathVariable Long id) {
        return ResponseEntity.ok(semesterService.getSemesterById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a semester (admin only)")
    public ResponseEntity<SemesterResponse> createSemester(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        LocalDate startDate = body.get("startDate") != null ? LocalDate.parse(body.get("startDate")) : null;
        LocalDate endDate = body.get("endDate") != null ? LocalDate.parse(body.get("endDate")) : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(semesterService.createSemester(name, startDate, endDate));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a semester (admin only)")
    public ResponseEntity<Void> deleteSemester(@PathVariable Long id) {
        semesterService.deleteSemester(id);
        return ResponseEntity.noContent().build();
    }
}
