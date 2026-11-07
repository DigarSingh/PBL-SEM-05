package com.shopsmart.storage;

/**
 * Binary storage for user-visible media. Keys are generated server-side (never from user
 * input). The local implementation writes to disk; an S3-compatible implementation can be
 * dropped in without changing callers.
 */
public interface StorageService {

    /** Stores the bytes under {@code key} and returns the public URL. */
    String store(String key, byte[] content, String contentType);

    void delete(String key);

    String publicUrl(String key);
}
