package com.xianfupu.shop.admin;

import com.xianfupu.shop.catalog.SearchAnalyticsService;
import com.xianfupu.shop.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/data")
public class AdminDataController {
    private final AdminDataService service;

    public AdminDataController(AdminDataService service) {
        this.service = service;
    }

    @GetMapping("/exports/{type}")
    ResponseEntity<byte[]> export(@PathVariable String type) {
        AdminDataService.ExportFile file = service.export(CurrentUser.id(), type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .header("X-Export-Row-Count", String.valueOf(file.rowCount()))
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(file.content());
    }

    @GetMapping("/search-keywords")
    List<SearchAnalyticsService.KeywordStat> searchKeywordStats(
            @RequestParam(defaultValue = "30") int days) {
        return service.searchKeywordStats(days);
    }
}
