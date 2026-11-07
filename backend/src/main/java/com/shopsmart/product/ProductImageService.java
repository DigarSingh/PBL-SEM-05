package com.shopsmart.product;

import com.shopsmart.admin.AuditService;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.product.dto.ImageResponse;
import com.shopsmart.storage.ImageProcessor;
import com.shopsmart.storage.StorageCleanup;
import com.shopsmart.storage.StorageService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Product image management. Files are written before the database rows; if the transaction
 * rolls back the files are removed, and files of deleted images are removed only after commit,
 * so storage and database never reference each other's missing data.
 */
@Service
public class ProductImageService {

    static final int MAX_IMAGES = 10;
    private static final int LARGE_SIZE = 1200;
    private static final int THUMB_SIZE = 400;

    private final ProductRepository productRepository;
    private final ImageProcessor imageProcessor;
    private final StorageService storage;
    private final StorageCleanup cleanup;
    private final AuditService auditService;

    public ProductImageService(ProductRepository productRepository, ImageProcessor imageProcessor,
                               StorageService storage, StorageCleanup cleanup, AuditService auditService) {
        this.productRepository = productRepository;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
        this.cleanup = cleanup;
        this.auditService = auditService;
    }

    @Transactional
    public List<ImageResponse> upload(Long productId, List<MultipartFile> files) {
        Product product = load(productId);
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("Select at least one image to upload");
        }
        if (product.getImages().size() + files.size() > MAX_IMAGES) {
            throw ApiException.businessRule("A product can have at most " + MAX_IMAGES + " images");
        }
        int nextOrder = product.getImages().stream().mapToInt(ProductImage::getSortOrder).max().orElse(-1) + 1;
        List<ProductImage> created = new ArrayList<>();
        for (MultipartFile file : files) {
            ImageProcessor.ProcessedImage processed = imageProcessor.process(read(file), LARGE_SIZE, THUMB_SIZE);
            String baseKey = "products/" + productId + "/" + UUID.randomUUID();
            String largeKey = baseKey + ".jpg";
            String thumbKey = thumbnailKey(largeKey);
            cleanup.deleteOnRollback(List.of(largeKey, thumbKey));
            String url = storage.store(largeKey, processed.large(), "image/jpeg");
            String thumbUrl = storage.store(thumbKey, processed.thumbnail(), "image/jpeg");

            ProductImage image = new ProductImage();
            image.setUrl(url);
            image.setThumbnailUrl(thumbUrl);
            image.setAltText(product.getName());
            image.setSortOrder(nextOrder++);
            image.setStorageKey(largeKey);
            image.setContentType("image/jpeg");
            image.setSizeBytes((long) processed.large().length);
            image.setWidth(processed.originalWidth());
            image.setHeight(processed.originalHeight());
            product.addImage(image);
            created.add(image);
        }
        productRepository.flush();
        auditService.record("PRODUCT_IMAGES_UPLOADED", "PRODUCT", productId, Map.of("count", created.size()));
        return created.stream().map(ProductMapper::image).toList();
    }

    @Transactional
    public ImageResponse addExternal(Long productId, String url, String altText) {
        Product product = load(productId);
        if (product.getImages().size() >= MAX_IMAGES) {
            throw ApiException.businessRule("A product can have at most " + MAX_IMAGES + " images");
        }
        ProductImage image = new ProductImage();
        image.setUrl(url.trim());
        image.setThumbnailUrl(url.trim());
        image.setAltText(StringUtils.hasText(altText) ? altText.trim() : product.getName());
        image.setSortOrder(product.getImages().stream().mapToInt(ProductImage::getSortOrder).max().orElse(-1) + 1);
        product.addImage(image);
        productRepository.flush();
        return ProductMapper.image(image);
    }

    @Transactional
    public List<ImageResponse> reorder(Long productId, List<Long> imageIds) {
        Product product = load(productId);
        List<Long> current = product.getImages().stream().map(ProductImage::getId).toList();
        if (imageIds.size() != current.size() || !new HashSet<>(imageIds).equals(new HashSet<>(current))) {
            throw ApiException.badRequest("The new order must list every image of the product exactly once");
        }
        for (ProductImage image : product.getImages()) {
            image.setSortOrder(imageIds.indexOf(image.getId()));
        }
        productRepository.flush();
        return product.getImages().stream()
                .sorted(java.util.Comparator.comparingInt(ProductImage::getSortOrder))
                .map(ProductMapper::image)
                .toList();
    }

    @Transactional
    public ImageResponse updateAltText(Long productId, Long imageId, String altText) {
        ProductImage image = findImage(load(productId), imageId);
        image.setAltText(StringUtils.hasText(altText) ? altText.trim() : null);
        return ProductMapper.image(image);
    }

    @Transactional
    public void delete(Long productId, Long imageId) {
        Product product = load(productId);
        ProductImage image = findImage(product, imageId);
        product.getImages().remove(image);
        if (image.getStorageKey() != null) {
            cleanup.deleteAfterCommit(List.of(image.getStorageKey(), thumbnailKey(image.getStorageKey())));
        }
        auditService.record("PRODUCT_IMAGE_DELETED", "PRODUCT", productId, Map.of("imageId", imageId));
    }

    private Product load(Long productId) {
        return productRepository.findById(productId).orElseThrow(() -> ApiException.notFound("Product"));
    }

    private static ProductImage findImage(Product product, Long imageId) {
        return product.getImages().stream().filter(image -> image.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Image"));
    }

    static String thumbnailKey(String largeKey) {
        return largeKey.replaceFirst("\\.jpg$", "-thumb.jpg");
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded file could not be read");
        }
    }
}
