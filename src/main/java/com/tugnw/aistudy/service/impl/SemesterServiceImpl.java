package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.semester.SemesterResponse;
import com.tugnw.aistudy.domain.entity.Semester;
import com.tugnw.aistudy.domain.entity.Subject;
import com.tugnw.aistudy.repository.SemesterRepository;
import com.tugnw.aistudy.repository.SubjectRepository;
import com.tugnw.aistudy.service.SemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SemesterServiceImpl implements SemesterService {

    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SemesterResponse> getAllSemesters() {
        return semesterRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SemesterResponse getSemesterById(UUID id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Semester not found"));
        return toResponse(semester);
    }

    @Override
    @Transactional
    public SemesterResponse createSemester(String name) {
        Semester semester = Semester.builder()
                .name(name)
                .build();
        Semester saved = semesterRepository.save(semester);

        // Auto-create default "General" subject for this semester
        Subject general = Subject.builder()
                .semester(saved)
                .name("General")
                .defaultSubject(true)
                .build();
        subjectRepository.save(general);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSemester(UUID id) {
        if (!semesterRepository.existsById(id))
            throw new RuntimeException("Semester not found");
        semesterRepository.deleteById(id);
    }

    private SemesterResponse toResponse(Semester semester) {
        return SemesterResponse.builder()
                .id(semester.getId())
                .name(semester.getName())
                .build();
    }
}
