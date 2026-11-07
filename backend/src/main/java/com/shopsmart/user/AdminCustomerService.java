package com.shopsmart.user;

import com.shopsmart.admin.AuditService;
import com.shopsmart.auth.RefreshTokenService;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.util.Money;
import com.shopsmart.order.OrderQueryService;
import com.shopsmart.user.dto.AdminCustomerDetail;
import com.shopsmart.user.dto.AdminCustomerSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminCustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AddressService addressService;
    private final OrderQueryService orderQueryService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public AdminCustomerService(CustomerRepository customerRepository, UserRepository userRepository,
                                AddressService addressService, OrderQueryService orderQueryService,
                                RefreshTokenService refreshTokenService, AuditService auditService) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.addressService = addressService;
        this.orderQueryService = orderQueryService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCustomerSummary> search(String q, UserStatus status, int page, int size) {
        String pattern = StringUtils.hasText(q) ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%" : null;
        Page<User> users = customerRepository.search(pattern, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<Long, Object[]> stats = stats(users.getContent().stream().map(User::getId).toList());
        return PageResponse.from(users, user -> summary(user, stats.get(user.getId())));
    }

    @Transactional(readOnly = true)
    public AdminCustomerDetail get(Long userId) {
        User user = loadCustomer(userId);
        Map<Long, Object[]> stats = stats(List.of(userId));
        return new AdminCustomerDetail(summary(user, stats.get(userId)), addressService.list(userId),
                orderQueryService.customerOrders(userId, null, 0, 5).items(), user.getLastLoginAt());
    }

    /** Disabling signs the customer out everywhere (refresh tokens revoked); access tokens expire within minutes. */
    @Transactional
    public AdminCustomerSummary changeStatus(Long userId, UserStatus status) {
        User user = loadCustomer(userId);
        UserStatus previous = user.getStatus();
        user.setStatus(status);
        if (status == UserStatus.DISABLED) {
            refreshTokenService.revokeAllForUser(userId);
        }
        auditService.record("CUSTOMER_STATUS_CHANGED", "USER", userId,
                Map.of("from", previous.name(), "to", status.name()));
        return summary(user, stats(List.of(userId)).get(userId));
    }

    private User loadCustomer(Long userId) {
        return userRepository.findWithRolesById(userId)
                .filter(user -> user.hasRole(Role.CUSTOMER))
                .orElseThrow(() -> ApiException.notFound("Customer"));
    }

    private Map<Long, Object[]> stats(List<Long> ids) {
        Map<Long, Object[]> stats = new HashMap<>();
        if (!ids.isEmpty()) {
            customerRepository.orderStats(ids).forEach(row -> stats.put((Long) row[0], row));
        }
        return stats;
    }

    private static AdminCustomerSummary summary(User user, Object[] stats) {
        long orders = stats == null ? 0 : (Long) stats[1];
        BigDecimal spent = stats == null ? Money.ZERO : Money.of((BigDecimal) stats[2]);
        Instant last = stats == null ? null : (Instant) stats[3];
        return new AdminCustomerSummary(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getStatus(), user.isEmailVerified(), orders, spent, last, user.getCreatedAt());
    }
}
