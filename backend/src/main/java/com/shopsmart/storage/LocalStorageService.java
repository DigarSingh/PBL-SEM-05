package com.shopsmart.storage;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Filesystem-backed storage served at {@code app.storage.public-url-prefix}. */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path root;
    private final String urlPrefix;

    public LocalStorageService(AppProperties properties) {
        this.root = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
        this.urlPrefix = properties.storage().publicUrlPrefix().replaceAll("/+$", "");
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return publicUrl(key);
        } catch (IOException e) {
            log.error("Failed to store file {}: {}", key, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.FILE_STORAGE_ERROR,
                    "The file could not be stored. Please try again.");
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Failed to delete stored file {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String publicUrl(String key) {
        return urlPrefix + "/" + key;
    }

    /** Resolves a key inside the storage root, refusing anything that would escape it. */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || key.contains("..")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return target;
    }
}
