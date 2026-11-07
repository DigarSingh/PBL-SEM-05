package com.shopsmart.analytics;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.util.BusinessTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** An inclusive range of business (IST) dates with its UTC boundaries for queries. */
public record Period(LocalDate from, LocalDate to) {

    public static final int MAX_DAYS = 366;

    public static Period of(LocalDate from, LocalDate to, Clock clock, int defaultDays) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(BusinessTime.ZONE));
        LocalDate start = from != null ? from : end.minusDays(defaultDays - 1L);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw ApiException.badRequest("The period may span at most " + MAX_DAYS + " days");
        }
        return new Period(start, end);
    }

    public Instant startInstant() {
        return from.atStartOfDay(BusinessTime.ZONE).toInstant();
    }

    public Instant endInstant() {
        return to.plusDays(1).atStartOfDay(BusinessTime.ZONE).toInstant();
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** The immediately preceding period of the same length (for growth comparisons). */
    public Period previous() {
        long days = days();
        return new Period(from.minusDays(days), from.minusDays(1));
    }
}
