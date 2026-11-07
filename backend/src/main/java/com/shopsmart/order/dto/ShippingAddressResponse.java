package com.shopsmart.order.dto;

public record ShippingAddressResponse(String fullName, String phone, String line1, String line2, String landmark,
                                      String city, String state, String postalCode, String country) {
}
