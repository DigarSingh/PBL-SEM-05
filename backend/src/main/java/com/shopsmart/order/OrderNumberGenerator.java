package com.shopsmart.order;

import com.shopsmart.common.util.BusinessTime;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Human-friendly, non-sequential order numbers such as {@code SS260923-7KQ4ZP}: date prefix for
 * support staff, random suffix so numbers do not reveal order volume.
 */
@Component
public class OrderNumberGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(BusinessTime.ZONE);

    private final SecureRandom random = new SecureRandom();
    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderNumberGenerator(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    public String next() {
        String prefix = "SS" + DATE.format(clock.instant()) + "-";
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder candidate = new StringBuilder(prefix);
            for (int i = 0; i < 6; i++) {
                candidate.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            if (!orderRepository.existsByOrderNumber(candidate.toString())) {
                return candidate.toString();
            }
        }
        throw new IllegalStateException("Could not allocate a unique order number");
    }
}
