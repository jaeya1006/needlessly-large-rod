package io.github.jaeya1006.needlesslylargerod.expense;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchedCategoryRepository extends JpaRepository<WatchedCategory, String> {
}
