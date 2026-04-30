package com.publicnext.analytics.controller;

import com.publicnext.analytics.domain.CustomerStats;
import com.publicnext.analytics.domain.DailyOrderMetrics;
import com.publicnext.analytics.domain.StatusCount;
import com.publicnext.analytics.service.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    @GetMapping("/daily")
    public List<DailyOrderMetrics> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        return queryService.dailyBetween(from, to);
    }

    @GetMapping("/status-distribution")
    public List<StatusCount> statusDistribution() {
        return queryService.statusDistribution();
    }

    @GetMapping("/top-customers")
    public List<CustomerStats> topCustomers(@RequestParam(defaultValue = "10") int limit) {
        if (limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 1000");
        }
        return queryService.topCustomers(limit);
    }
}
