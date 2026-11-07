package com.shopsmart.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Two clearly separated sections: {@code historical} facts computed from recorded orders, and
 * {@code predictions} produced by the forecasting model (with its own availability status).
 */
public record InsightsResponse(LocalDate from, LocalDate to, List<Insight> historical, Predictions predictions) {

    public record Insight(String type, String severity, String title, String message, Long productId,
                          BigDecimal metric) {
    }

    public record Predictions(boolean available, String status, String model, Integer horizonDays,
                              Integer leadTimeDays, Instant generatedAt, String message, List<Forecast> items) {
    }

    public record Forecast(Long productId, String name, String status, Double predictedUnits, Double lower,
                           Double upper, Double dailyRate, String trend, Integer available, Double daysOfCover,
                           Integer restockQuantity, String note) {
    }
}
