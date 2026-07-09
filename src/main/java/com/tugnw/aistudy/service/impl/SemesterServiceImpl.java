package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.semester.SemesterResponse;
import com.tugnw.aistudy.domain.entity.Semester;
import com.tugnw.aistudy.repository.SemesterRepository;
import com.tugnw.aistudy.service.SemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SemesterServiceImpl implements SemesterService {

    private final SemesterRepository semesterRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SemesterResponse> getAllSemesters() {
        return semesterRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SemesterResponse getSemesterById(Long id) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Semester not found"));
        return toResponse(semester);
    }

    @Override
    public SemesterResponse createSemester(String name, LocalDate startDate, LocalDate endDate) {
        Semester semester = Semester.builder()
                .name(name)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        return toResponse(semesterRepository.save(semester));
    }

    @Override
    public void deleteSemester(Long id) {
        if (!semesterRepository.existsById(id)) {
            throw new RuntimeException("Semester not found");
        }
        semesterRepository.deleteById(id);
    }

    private SemesterResponse toResponse(Semester semester) {
        return SemesterResponse.builder()
                .id(semester.getId())
                .name(semester.getName())
                .startDate(semester.getStartDate())
                .endDate(semester.getEndDate())
                .build();
    }
}
