package io.github.jaeya1006.needlesslylargerod.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 통계에서 항상 지난달과 비교할 분류(큰 분류 이름).
 * 예전(로그인 전) watched_category 테이블은 주인이 없어서 새 테이블을 쓴다. 예전 값은 첫 회원에게 복사된다.
 */
@Entity
@Table(name = "member_watched_category", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "category"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchedCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 30)
    private String category;

    public WatchedCategory(Long ownerId, String category) {
        this.ownerId = ownerId;
        this.category = category;
    }
}
