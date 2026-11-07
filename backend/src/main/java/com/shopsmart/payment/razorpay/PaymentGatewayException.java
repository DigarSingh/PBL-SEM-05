package com.shopsmart.payment.razorpay;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** A Razorpay call failed; the message is safe to show to users. */
public class PaymentGatewayException extends ApiException {

    public PaymentGatewayException(String message) {
        this(HttpStatus.BAD_GATEWAY, message);
    }

    public PaymentGatewayException(HttpStatus status, String message) {
        super(status, ErrorCode.PAYMENT_GATEWAY_ERROR, message);
    }
}
