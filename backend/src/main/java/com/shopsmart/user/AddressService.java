package com.shopsmart.user;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.user.dto.AddressRequest;
import com.shopsmart.user.dto.AddressResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AddressService {

    static final int MAX_ADDRESSES = 20;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId).stream()
                .map(UserMapper::toAddress)
                .toList();
    }

    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw ApiException.businessRule("You can save up to " + MAX_ADDRESSES + " addresses");
        }
        Address address = new Address();
        address.setUser(userRepository.getReferenceById(userId));
        apply(address, request);
        // The first address always becomes the default.
        address.setDefaultAddress(request.isDefault() || existing == 0);
        addressRepository.save(address);
        if (address.isDefaultAddress()) {
            addressRepository.clearDefaultExcept(userId, address.getId());
        }
        return UserMapper.toAddress(address);
    }

    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        Address address = loadOwned(userId, addressId);
        apply(address, request);
        if (request.isDefault() && !address.isDefaultAddress()) {
            address.setDefaultAddress(true);
            addressRepository.clearDefaultExcept(userId, address.getId());
        }
        return UserMapper.toAddress(address);
    }

    @Transactional
    public AddressResponse makeDefault(Long userId, Long addressId) {
        Address address = loadOwned(userId, addressId);
        address.setDefaultAddress(true);
        addressRepository.clearDefaultExcept(userId, address.getId());
        return UserMapper.toAddress(address);
    }

    /** Orders keep their own address snapshot, so deleting an address never affects past orders. */
    @Transactional
    public void delete(Long userId, Long addressId) {
        Address address = loadOwned(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasDefault) {
            addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    @Transactional(readOnly = true)
    public Address loadOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ApiException.notFound("Address"));
    }

    private static void apply(Address address, AddressRequest request) {
        address.setLabel(trimToNull(request.label()));
        address.setFullName(request.fullName().trim());
        address.setPhone(request.phone().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(trimToNull(request.line2()));
        address.setLandmark(trimToNull(request.landmark()));
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setCountry(StringUtils.hasText(request.country()) ? request.country() : "IN");
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
