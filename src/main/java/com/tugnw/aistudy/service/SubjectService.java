package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.subject.SubjectResponse;

import java.util.List;

public interface SubjectService {

    List<SubjectResponse> getSubjectsBySemester(Long semesterId);

    SubjectResponse getSubjectById(Long id);

    SubjectResponse createSubject(Long semesterId, String code, String name);

    void deleteSubject(Long id);
}
