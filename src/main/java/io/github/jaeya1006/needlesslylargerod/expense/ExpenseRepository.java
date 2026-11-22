package io.github.jaeya1006.needlesslylargerod.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByOwnerIdOrderBySpentAtDescIdDesc(Long ownerId);

    List<Expense> findByOwnerIdAndSpentAtBetweenOrderBySpentAtDescIdDesc(Long ownerId, LocalDate from, LocalDate to);

    Optional<Expense> findByIdAndOwnerId(Long id, Long ownerId);

    /** 로그인 기능 전에 쌓인(주인 없는) 지출을 넘긴다 */
    @Modifying
    @Query("update Expense e set e.ownerId = :ownerId where e.ownerId is null")
    int claimOwnerless(@Param("ownerId") Long ownerId);
}
