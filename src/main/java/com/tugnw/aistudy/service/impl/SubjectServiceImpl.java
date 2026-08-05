package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.subject.SubjectResponse;
import com.tugnw.aistudy.domain.entity.Semester;
import com.tugnw.aistudy.domain.entity.Subject;
import com.tugnw.aistudy.repository.SemesterRepository;
import com.tugnw.aistudy.repository.SubjectRepository;
import com.tugnw.aistudy.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final SemesterRepository semesterRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> getSubjectsBySemester(UUID semesterId) {
        return subjectRepository.findBySemesterIdOrderByNameAsc(semesterId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> getAllSubjects() {
        return subjectRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SubjectResponse getSubjectById(UUID id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found"));
        return toResponse(subject);
    }

    @Override
    @Transactional
    public SubjectResponse createSubject(UUID semesterId, String code, String name) {
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new RuntimeException("Semester not found"));

        Subject subject = Subject.builder()
                .semester(semester)
                .code(code)
                .name(name)
                .defaultSubject(false)
                .build();
        return toResponse(subjectRepository.save(subject));
    }

    @Override
    @Transactional
    public void deleteSubject(UUID id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        if (Boolean.TRUE.equals(subject.getDefaultSubject()))
            throw new RuntimeException("Cannot delete the default subject");

        subjectRepository.deleteById(id);
    }

    private SubjectResponse toResponse(Subject subject) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .semesterId(subject.getSemester().getId())
                .code(subject.getCode())
                .name(subject.getName())
                .defaultSubject(subject.getDefaultSubject())
                .build();
    }
}
