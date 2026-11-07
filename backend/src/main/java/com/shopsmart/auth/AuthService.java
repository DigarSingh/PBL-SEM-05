package com.shopsmart.auth;

import com.shopsmart.admin.AuditService;
import com.shopsmart.auth.dto.AuthResponse;
import com.shopsmart.auth.dto.LoginRequest;
import com.shopsmart.auth.dto.RegisterRequest;
import com.shopsmart.auth.dto.RegisterResponse;
import com.shopsmart.auth.events.EmailVerifiedEvent;
import com.shopsmart.auth.events.PasswordResetRequestedEvent;
import com.shopsmart.auth.events.UserRegisteredEvent;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.Emails;
import com.shopsmart.config.AppProperties;
import com.shopsmart.security.JwtTokenService;
import com.shopsmart.user.Role;
import com.shopsmart.user.RoleRepository;
import com.shopsmart.user.User;
import com.shopsmart.user.UserMapper;
import com.shopsmart.user.UserRepository;
import com.shopsmart.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final UserTokenService userTokenService;
    private final ApplicationEventPublisher events;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Clock clock;
    /** Hash of a random value, used to equalise timing when the e-mail is unknown. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService,
                       UserTokenService userTokenService, ApplicationEventPublisher events,
                       AuditService auditService, AppProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.userTokenService = userTokenService;
        this.events = events;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-" + System.nanoTime());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.duplicate("An account with this email already exists");
        }
        Role customerRole = roleRepository.findByName(Role.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role missing"));
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(customerRole);

        boolean verificationRequired = properties.security().requireEmailVerification();
        if (!verificationRequired) {
            user.setEmailVerifiedAt(clock.instant());
        }
        userRepository.save(user);
        auditService.record(user.getId(), email, "USER_REGISTERED", "USER", user.getId(), Map.of());

        if (verificationRequired) {
            String token = userTokenService.issue(user, UserTokenType.EMAIL_VERIFICATION,
                    properties.security().emailVerificationTtl());
            events.publishEvent(new UserRegisteredEvent(user.getId(), email, user.getFullName(), token));
        } else {
            events.publishEvent(new EmailVerifiedEvent(user.getId(), email, user.getFullName()));
        }
        return new RegisterResponse(user.getId(), email, verificationRequired);
    }

    /**
     * Verifies credentials. The password is checked before account state so an attacker cannot
     * learn whether an account is locked, disabled or unverified without knowing its password.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(LoginRequest request, String ip, String userAgent) {
        Instant now = clock.instant();
        String email = Emails.normalize(request.email());
        User user = userRepository.findWithRolesByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.LOCKED, ErrorCode.ACCOUNT_LOCKED,
                    "Too many failed sign-in attempts. Try again in " + minutes + " minute(s).");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user, now);
            throw invalidCredentials();
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_DISABLED,
                    "This account has been disabled. Please contact support.");
        }
        if (properties.security().requireEmailVerification() && !user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED,
                    "Please verify your email address before signing in.");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        String refreshToken = refreshTokenService.issue(user, ip, userAgent);
        return new LoginResult(buildAuthResponse(user), refreshToken);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult refresh(String rawRefreshToken, String ip, String userAgent) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, "No active session");
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, ip, userAgent);
        User user = rotation.user();
        if (user.getStatus() != UserStatus.ACTIVE || user.isLocked(clock.instant())) {
            refreshTokenService.revokeAllForUser(user.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, "No active session");
        }
        return new LoginResult(buildAuthResponse(user), rotation.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (StringUtils.hasText(rawRefreshToken)) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        User user = userTokenService.consume(rawToken, UserTokenType.EMAIL_VERIFICATION);
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(clock.instant());
            events.publishEvent(new EmailVerifiedEvent(user.getId(), user.getEmail(), user.getFullName()));
        }
    }

    /** Always succeeds from the caller's perspective so it cannot be used to probe for accounts. */
    @Transactional
    public void resendVerification(String rawEmail) {
        userRepository.findByEmail(Emails.normalize(rawEmail))
                .filter(user -> !user.isEmailVerified() && user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    String token = userTokenService.issue(user, UserTokenType.EMAIL_VERIFICATION,
                            properties.security().emailVerificationTtl());
                    events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), user.getFullName(),
                            token));
                });
    }

    /** Always succeeds from the caller's perspective so it cannot be used to probe for accounts. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        userRepository.findByEmail(Emails.normalize(rawEmail))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    String token = userTokenService.issue(user, UserTokenType.PASSWORD_RESET,
                            properties.security().passwordResetTtl());
                    events.publishEvent(new PasswordResetRequestedEvent(user.getId(), user.getEmail(),
                            user.getFullName(), token));
                    log.info("Password reset requested for user {}", user.getId());
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = userTokenService.consume(rawToken, UserTokenType.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (!user.isEmailVerified()) {
            // Completing a reset proves control of the mailbox.
            user.setEmailVerifiedAt(clock.instant());
        }
        refreshTokenService.revokeAllForUser(user.getId());
        auditService.record(user.getId(), user.getEmail(), "PASSWORD_RESET", "USER", user.getId(), Map.of());
    }

    private void registerFailedAttempt(User user, Instant now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= properties.security().maxFailedLogins()) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(now.plus(properties.security().lockoutDuration()));
            auditService.record(user.getId(), user.getEmail(), "ACCOUNT_LOCKED", "USER", user.getId(),
                    Map.of("reason", "too many failed sign-in attempts"));
            log.warn("User {} locked after {} failed sign-in attempts", user.getId(), attempts);
        } else {
            user.setFailedLoginAttempts(attempts);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        JwtTokenService.IssuedToken token = jwtTokenService.issueAccessToken(user.getId(), user.getEmail(),
                user.getFullName(), user.roleNames());
        return AuthResponse.bearer(token.value(), token.expiresAt(), UserMapper.toProfile(user));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }

    public record LoginResult(AuthResponse response, String refreshToken) {
    }
}
