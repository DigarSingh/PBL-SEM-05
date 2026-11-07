package com.shopsmart.activity;

import com.shopsmart.activity.dto.ActivityRequest;
import com.shopsmart.category.CategoryRepository;
import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Activity", description = "Browsing signals that personalise recommendations")
@RestController
public class ActivityController {

    private final ActivityService activityService;
    private final CategoryRepository categoryRepository;

    public ActivityController(ActivityService activityService, CategoryRepository categoryRepository) {
        this.activityService = activityService;
        this.categoryRepository = categoryRepository;
    }

    @Operation(summary = "Record that the signed-in user browsed a category")
    @PostMapping("/api/activity/category-views")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> categoryView(@CurrentUser AuthUser user, @Valid @RequestBody ActivityRequest request) {
        if (!categoryRepository.existsById(request.categoryId())) {
            throw ApiException.notFound("Category");
        }
        activityService.record(user.id(), ActivityType.CATEGORY_VIEW, null, request.categoryId(), null);
        return ApiResponse.message("Recorded");
    }
}
