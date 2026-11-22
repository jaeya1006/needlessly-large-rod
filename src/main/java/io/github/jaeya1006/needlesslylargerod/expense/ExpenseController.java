package io.github.jaeya1006.needlesslylargerod.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.github.jaeya1006.needlesslylargerod.auth.CurrentMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public List<Expense> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) YearMonth month) {
        long me = CurrentMember.id(jwt);
        if (month == null) return repository.findByOwnerIdOrderBySpentAtDescIdDesc(me);
        return repository.findByOwnerIdAndSpentAtBetweenOrderBySpentAtDescIdDesc(me, month.atDay(1), month.atEndOfMonth());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Expense create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExpenseRequest req) {
        return repository.save(new Expense(CurrentMember.id(jwt), req.type(), blankToNull(req.title()), req.amount(),
                req.category(), blankToNull(req.subcategory()), blankToNull(req.memo()), req.spentAt()));
    }

    @PutMapping("/{id}")
    public Expense update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ExpenseRequest req) {
        Expense e = repository.findByIdAndOwnerId(id, CurrentMember.id(jwt)) // 남의 내역은 없는 것처럼 404
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        e.update(req.type(), blankToNull(req.title()), req.amount(),
                req.category(), blankToNull(req.subcategory()), blankToNull(req.memo()), req.spentAt());
        return repository.save(e);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        repository.delete(repository.findByIdAndOwnerId(id, CurrentMember.id(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
