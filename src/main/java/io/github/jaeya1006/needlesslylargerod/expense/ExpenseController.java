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
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseRepository repository;

    public record ExpenseRequest(
            @NotNull Expense.Type type,
            @Size(max = 100) String title,
            @Positive long amount,
            @NotBlank @Size(max = 30) String category,
            @Size(max = 30) String subcategory,
            @Size(max = 500) String memo,
            @NotNull LocalDate spentAt) {
    }

    /** month(예: 2026-10)를 주면 그 달만, 없으면 전체. */
    @GetMapping
    public List<Expense> list(@RequestParam(required = false) YearMonth month) {
        if (month == null) return repository.findAllByOrderBySpentAtDescIdDesc();
        return repository.findBySpentAtBetweenOrderBySpentAtDescIdDesc(month.atDay(1), month.atEndOfMonth());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Expense create(@Valid @RequestBody ExpenseRequest req) {
        return repository.save(new Expense(req.type(), blankToNull(req.title()), req.amount(),
                req.category(), blankToNull(req.subcategory()), blankToNull(req.memo()), req.spentAt()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
