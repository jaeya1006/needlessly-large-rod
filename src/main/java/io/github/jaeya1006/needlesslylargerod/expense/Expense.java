package io.github.jaeya1006.needlesslylargerod.expense;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private long amount; // 원 단위 정수

    private String category;

    private LocalDate spentAt;

    public Expense(String title, long amount, String category, LocalDate spentAt) {
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.spentAt = spentAt;
    }
}
