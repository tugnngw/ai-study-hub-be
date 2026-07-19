package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.semester.SemesterResponse;


import java.util.List;
import java.util.UUID;

public interface SemesterService {

    List<SemesterResponse> getAllSemesters();

    SemesterResponse getSemesterById(UUID id);

    SemesterResponse createSemester(String name);

    void deleteSemester(UUID id);
}
