package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.subject.SubjectResponse;

import java.util.List;
import java.util.UUID;

public interface SubjectService {

    List<SubjectResponse> getSubjectsBySemester(UUID semesterId);

    SubjectResponse getSubjectById(UUID id);

    SubjectResponse createSubject(UUID semesterId, String code, String name);

    void deleteSubject(UUID id);
}
