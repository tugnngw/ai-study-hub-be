package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findBySemesterIdOrderByNameAsc(UUID semesterId);

    List<Subject> findAllByOrderByNameAsc();
}
