package io.github.jaeya1006.needlesslylargerod.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WatchedCategoryRepository extends JpaRepository<WatchedCategory, Long> {

    List<WatchedCategory> findByOwnerIdOrderByCategory(Long ownerId);

    @Modifying
    @Query("delete from WatchedCategory w where w.ownerId = :ownerId")
    void deleteByOwner(@Param("ownerId") Long ownerId);
}
