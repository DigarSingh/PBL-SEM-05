package com.shopsmart.product;

public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    public static StockStatus of(int available, int lowStockThreshold) {
        if (available <= 0) {
            return OUT_OF_STOCK;
        }
        return available <= lowStockThreshold ? LOW_STOCK : IN_STOCK;
    }
}
