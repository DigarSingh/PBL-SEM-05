package com.shopsmart.ai;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API for the AI service (X-Internal-Api-Key). Not routed publicly by Nginx. */
@Hidden
@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {

    private final InternalAiDataService dataService;

    public InternalAiController(InternalAiDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return dataService.catalog();
    }

    @GetMapping("/interactions")
    public Map<String, Object> interactions(@RequestParam(defaultValue = "180") @Min(1) @Max(730) int days) {
        return dataService.interactions(days);
    }
}
