package org.example.taxi.repository;

import org.example.taxi.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.Optional;
import java.util.List;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {
    Optional<Goal> findByMonth(YearMonth month);
    List<Goal> findByMonthGreaterThanEqualOrderByMonthAsc(YearMonth month);
    List<Goal> findByMonthLessThanEqualOrderByMonthDesc(YearMonth month);
    List<Goal> findAllByOrderByMonthAsc();
}