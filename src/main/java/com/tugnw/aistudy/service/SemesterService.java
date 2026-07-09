package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.semester.SemesterResponse;

import java.util.List;

public interface SemesterService {

    List<SemesterResponse> getAllSemesters();

    SemesterResponse getSemesterById(Long id);

    SemesterResponse createSemester(String name, java.time.LocalDate startDate, java.time.LocalDate endDate);

    void deleteSemester(Long id);
}
