package io.github.jaeya1006.needlesslylargerod.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/api/watched-categories")
@RequiredArgsConstructor
public class WatchedCategoryController {

    private final WatchedCategoryRepository repository;

    @GetMapping
    public List<String> list() {
        return repository.findAll().stream().map(WatchedCategory::getCategory).sorted().toList();
    }

    /** 체크 목록을 통째로 바꾼다. 중복은 하나로 합친다. */
    @PutMapping
    @Transactional
    public List<String> replace(@RequestBody @NotNull @Size(max = 50) List<@NotBlank @Size(max = 30) String> categories) {
        repository.deleteAllInBatch();
        repository.saveAll(new LinkedHashSet<>(categories).stream().map(String::strip).distinct().map(WatchedCategory::new).toList());
        return list();
    }
}
