package com.shopsmart.product;

/**
 * DRAFT products are being prepared, ACTIVE ones are sold, INACTIVE ones are temporarily
 * hidden and ARCHIVED is the soft-deleted state (kept because orders reference them).
 */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
