package com.shopsmart.common.util;

import java.time.ZoneId;

/** The store operates in India: business dates (order numbers, daily analytics) use IST. */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private BusinessTime() {
    }
}
