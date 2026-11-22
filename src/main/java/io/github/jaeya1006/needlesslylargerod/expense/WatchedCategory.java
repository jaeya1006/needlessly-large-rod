package io.github.jaeya1006.needlesslylargerod.expense;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 통계에서 항상 지난달과 비교할 분류(큰 분류 이름). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WatchedCategory {

    @Id
    private String category;
}
