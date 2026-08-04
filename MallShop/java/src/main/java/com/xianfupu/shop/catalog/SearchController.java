package com.xianfupu.shop.catalog;

import com.xianfupu.shop.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/searches")
public class SearchController {
    private final SearchAnalyticsService service;

    public SearchController(SearchAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/hot")
    List<SearchAnalyticsService.HotKeyword> hot(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return service.hot(days, limit);
    }

    @GetMapping("/history")
    List<SearchAnalyticsService.SearchHistory> history() {
        return service.history(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SearchAnalyticsService.SearchHistory record(@Valid @RequestBody SearchRequest request) {
        return service.record(CurrentUser.id(), request.keyword());
    }

    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearHistory() {
        service.clearHistory(CurrentUser.id());
    }

    public record SearchRequest(@NotBlank @Size(max = 80) String keyword) {
    }
}
