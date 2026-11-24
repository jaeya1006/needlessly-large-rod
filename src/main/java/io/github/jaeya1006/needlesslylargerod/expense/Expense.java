package io.github.jaeya1006.needlesslylargerod.expense;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore // 화면에 보낼 필요 없음
    private Long ownerId; // 회원 id. 로그인 기능 전 데이터는 null → 첫 회원에게 넘긴다

    @Enumerated(EnumType.STRING)
    private Type type;

    private String title; // 내역, 비어 있으면 화면에서 분류 이름을 보여 줌

    private long amount; // 원 단위 정수

    private String category;

    private String subcategory; // 세부 분류(예: 교통 > 택시), 없으면 null

    private String memo;

    @Getter(AccessLevel.NONE) // JSON에는 isWasted()로 true/false만 나간다
    private Boolean wasted; // '쓸데없었다' 표시. null(예전 행)은 false로 본다

    public boolean isWasted() {
        return Boolean.TRUE.equals(wasted);
    }

    public void markWasted(boolean wasted) {
        this.wasted = wasted;
    }

    private LocalDate spentAt;

    public Expense(Long ownerId, Type type, String title, long amount, String category, String subcategory, String memo, LocalDate spentAt) {
        this.ownerId = ownerId;
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
