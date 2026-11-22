package io.github.jaeya1006.needlesslylargerod.expense;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    public enum Type { EXPENSE, INCOME }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Type type;

    private String title; // 내역, 비어 있으면 화면에서 분류 이름을 보여 줌

    private long amount; // 원 단위 정수

    private String category;

    private String subcategory; // 세부 분류(예: 교통 > 택시), 없으면 null

    private String memo;

    private LocalDate spentAt;

    public Expense(Type type, String title, long amount, String category, String subcategory, String memo, LocalDate spentAt) {
        this.type = type;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.subcategory = subcategory;
        this.memo = memo;
        this.spentAt = spentAt;
    }

    public void update(Type type, String title, long amount, String category, String subcategory, String memo, LocalDate spentAt) {
        this.type = type;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.subcategory = subcategory;
        this.memo = memo;
        this.spentAt = spentAt;
    }
}
