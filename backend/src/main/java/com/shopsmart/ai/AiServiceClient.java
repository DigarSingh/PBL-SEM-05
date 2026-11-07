package com.shopsmart.ai;

import com.shopsmart.config.AppProperties;
import com.shopsmart.security.InternalApiKeyFilter;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client for the Python AI service. The AI service is optional: every call has short timeouts,
 * failures return {@link Optional#empty()} so callers fall back to database logic, and a small
 * circuit breaker stops calling for a while after repeated failures so an outage adds no latency.
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    private static final int FAILURES_TO_OPEN = 3;
    private static final Duration OPEN_FOR = Duration.ofSeconds(30);

    private final AppProperties.Ai config;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final RestClient restClient;
    private final RestClient slowRestClient;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant openUntil = Instant.EPOCH;

    public AiServiceClient(AppProperties properties, RestClient.Builder builder, JsonMapper jsonMapper, Clock clock) {
        this.config = properties.ai();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.restClient = build(builder, config.readTimeout());
        this.slowRestClient = build(builder, config.readTimeout().multipliedBy(5));
    }

    private RestClient build(RestClient.Builder builder, Duration readTimeout) {
        // HTTP/1.1: over plain http the JDK client would otherwise attempt an h2c upgrade, which
        // ASGI servers such as uvicorn do not support for requests with a body.
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(config.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        RestClient.Builder configured = builder.clone().baseUrl(config.baseUrl()).requestFactory(factory);
        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            configured.defaultHeader(InternalApiKeyFilter.HEADER, config.apiKey());
        }
        return configured.build();
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    /** Semantic ranking of candidate products for a free-text query. */
    public Optional<Ranking> rank(String query, List<Long> candidateIds, int limit) {
        return post("/v1/search/rank", Map.of("query", query, "candidate_ids", candidateIds, "limit", limit), false)
                .map(node -> new Ranking(node.path("backend").asString("unknown"), scores(node)));
    }

    public Optional<List<Scored>> similar(Long productId, int limit, List<Long> excludeIds) {
        return post("/v1/recommendations/similar", Map.of("product_id", productId, "limit", limit,
                "exclude_ids", excludeIds), false).map(this::scores);
    }

    public Optional<List<Scored>> forUser(List<Map<String, Object>> events, List<String> queries,
                                          List<Long> excludeIds, int limit) {
        return post("/v1/recommendations/user", Map.of("events", events, "queries", queries,
                "exclude_ids", excludeIds, "limit", limit), false).map(this::scores);
    }

    public Optional<JsonNode> forecast(Map<String, Object> request) {
        return post("/v1/forecast/demand", request, true);
    }

    public Optional<JsonNode> health() {
        if (!config.enabled()) {
            return Optional.empty();
        }
        try {
            String body = restClient.get().uri("/health").retrieve().body(String.class);
            return Optional.of(jsonMapper.readTree(body));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<JsonNode> post(String path, Object body, boolean slow) {
        if (!config.enabled() || clock.instant().isBefore(openUntil)) {
            return Optional.empty();
        }
        try {
            String response = (slow ? slowRestClient : restClient).post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
            consecutiveFailures.set(0);
            return Optional.of(jsonMapper.readTree(response));
        } catch (RuntimeException e) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= FAILURES_TO_OPEN) {
                openUntil = clock.instant().plus(OPEN_FOR);
                consecutiveFailures.set(0);
                log.warn("AI service unavailable ({}); using fallbacks for {}s", e.getMessage(), OPEN_FOR.toSeconds());
            } else {
                log.debug("AI service call {} failed: {}", path, e.getMessage());
            }
            return Optional.empty();
        }
    }

    private List<Scored> scores(JsonNode node) {
        return node.path("results").valueStream()
                .map(item -> new Scored(item.path("product_id").asLong(), item.path("score").asDouble(),
                        item.path("reason").asString(null)))
                .toList();
    }

    public record Scored(Long productId, double score, String reason) {
    }

    public record Ranking(String backend, List<Scored> results) {
    }
}
