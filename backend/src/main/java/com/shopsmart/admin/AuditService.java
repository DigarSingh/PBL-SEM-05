package com.shopsmart.admin;

import com.shopsmart.common.web.RequestMetadata;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.SecurityUtils;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records security-relevant and administrative actions. Entries join the caller's
 * transaction so an audit row exists exactly when the audited change is committed.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAILS = 2000;

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;

    public AuditService(AuditLogRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, Object entityId, Map<String, ?> details) {
        AuthUser actor = SecurityUtils.currentUser().orElse(null);
        record(actor == null ? null : actor.id(), actor == null ? null : actor.email(), action, entityType, entityId,
                details);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Long actorUserId, String actorEmail, String action, String entityType, Object entityId,
                       Map<String, ?> details) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actorUserId);
        entry.setActorEmail(actorEmail);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setDetails(serialize(details));
        entry.setIpAddress(RequestMetadata.clientIp());
        repository.save(entry);
    }

    private String serialize(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            String json = jsonMapper.writeValueAsString(details);
            return json.length() > MAX_DETAILS ? json.substring(0, MAX_DETAILS) : json;
        } catch (RuntimeException e) {
            log.warn("Could not serialise audit details: {}", e.getMessage());
            return null;
        }
    }
}
