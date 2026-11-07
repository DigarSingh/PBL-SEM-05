package com.shopsmart.inventory;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Raised when requested quantities exceed what is available; lists every affected item. */
public class InsufficientStockException extends ApiException {

    public InsufficientStockException(List<Shortage> shortages) {
        super(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK, message(shortages),
                Map.of("items", shortages));
    }

    private static String message(List<Shortage> shortages) {
        if (shortages.size() == 1) {
            Shortage shortage = shortages.getFirst();
            return shortage.available() <= 0
                    ? shortage.name() + " is out of stock"
                    : "Only " + shortage.available() + " unit(s) of " + shortage.name() + " are available";
        }
        return "Some items in your cart are no longer available in the requested quantity";
    }

    public record Shortage(Long variantId, String sku, String name, int requested, int available) {
    }
}
