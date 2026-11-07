package com.shopsmart.user;

import com.shopsmart.admin.AuditService;
import com.shopsmart.auth.RefreshTokenService;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.user.dto.ChangePasswordRequest;
import com.shopsmart.user.dto.UpdateProfileRequest;
import com.shopsmart.user.dto.UserProfileResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserMapper.toProfile(load(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = load(userId);
        user.setFullName(request.fullName().trim());
        user.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        return UserMapper.toProfile(user);
    }

    /** Changing the password signs the user out everywhere else by revoking all refresh tokens. */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CREDENTIALS,
                    "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("The new password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(userId);
        auditService.record(userId, user.getEmail(), "PASSWORD_CHANGED", "USER", userId, Map.of());
    }

    @Transactional
    public UserProfileResponse updateAvatar(Long userId, String avatarUrl) {
        User user = load(userId);
        user.setAvatarUrl(avatarUrl);
        return UserMapper.toProfile(user);
    }

    User load(Long userId) {
        return userRepository.findWithRolesById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
