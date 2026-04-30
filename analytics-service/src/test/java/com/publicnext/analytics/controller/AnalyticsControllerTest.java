package com.publicnext.analytics.controller;

import com.publicnext.analytics.domain.CustomerStats;
import com.publicnext.analytics.domain.DailyOrderMetrics;
import com.publicnext.analytics.domain.StatusCount;
import com.publicnext.analytics.service.AnalyticsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsQueryService queryService;

    @Test
    void daily_returnsRowsInRange() throws Exception {
        when(queryService.dailyBetween(eq(LocalDate.of(2026, 4, 28)), eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(List.of(
                        new DailyOrderMetrics("2026-04-28", 2, 1, 0, new BigDecimal("20.00")),
                        new DailyOrderMetrics("2026-04-29", 3, 2, 1, new BigDecimal("45.50"))
                ));

        mockMvc.perform(get("/api/v1/analytics/daily")
                        .param("from", "2026-04-28")
                        .param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].day").value("2026-04-28"))
                .andExpect(jsonPath("$[0].ordersCreated").value(2))
                .andExpect(jsonPath("$[0].revenue").value(20.00))
                .andExpect(jsonPath("$[1].day").value("2026-04-29"))
                .andExpect(jsonPath("$[1].ordersCancelled").value(1));
    }

    @Test
    void daily_fromAfterTo_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/daily")
                        .param("from", "2026-04-30")
                        .param("to", "2026-04-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void daily_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/daily")
                        .param("from", "2026-04-30"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusDistribution_returnsAllCounts() throws Exception {
        when(queryService.statusDistribution()).thenReturn(List.of(
                new StatusCount("UNPROCESSED", 5L),
                new StatusCount("PROCESSING", 2L)
        ));

        mockMvc.perform(get("/api/v1/analytics/status-distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[?(@.status=='UNPROCESSED')].count").value(5))
                .andExpect(jsonPath("$[?(@.status=='PROCESSING')].count").value(2));
    }

    @Test
    void topCustomers_defaultLimitIs10() throws Exception {
        when(queryService.topCustomers(10)).thenReturn(List.of(
                new CustomerStats("c-1", 4, new BigDecimal("400.00"))
        ));

        mockMvc.perform(get("/api/v1/analytics/top-customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value("c-1"))
                .andExpect(jsonPath("$[0].totalSpent").value(400.00));
    }

    @Test
    void topCustomers_explicitLimitIsForwarded() throws Exception {
        when(queryService.topCustomers(3)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/top-customers").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void topCustomers_zeroLimit_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/top-customers").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }
}
