package io.github.jaeya1006.needlesslylargerod.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByOrderBySpentAtDescIdDesc();

    List<Expense> findBySpentAtBetweenOrderBySpentAtDescIdDesc(LocalDate from, LocalDate to);
}
