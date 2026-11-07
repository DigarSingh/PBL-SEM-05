package com.shopsmart.user;

import com.shopsmart.user.dto.AddressResponse;
import com.shopsmart.user.dto.UserProfileResponse;
import java.util.List;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), List.copyOf(user.roleNames()), user.isEmailVerified(), user.getCreatedAt());
    }

    public static AddressResponse toAddress(Address address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getFullName(), address.getPhone(),
                address.getLine1(), address.getLine2(), address.getLandmark(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getCountry(), address.isDefaultAddress());
    }
}
