package com.shopsmart.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeriesPoint(LocalDate period, BigDecimal revenue, long orders, long units) {
}
