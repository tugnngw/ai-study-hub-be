package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findBySemesterIdOrderByNameAsc(Long semesterId);

    List<Subject> findAllByOrderByNameAsc();
}
