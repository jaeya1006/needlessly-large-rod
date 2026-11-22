package io.github.jaeya1006.needlesslylargerod.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseRepository repository;

    public record ExpenseRequest(
            @NotBlank @Size(max = 100) String title,
            @Positive long amount,
            @NotBlank @Size(max = 30) String category,
            @NotNull LocalDate spentAt) {
    }

    @GetMapping
    public List<Expense> list() {
        return repository.findAllByOrderBySpentAtDescIdDesc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Expense create(@Valid @RequestBody ExpenseRequest req) {
        return repository.save(new Expense(req.title(), req.amount(), req.category(), req.spentAt()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
