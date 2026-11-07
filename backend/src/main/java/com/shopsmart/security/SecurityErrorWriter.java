package com.shopsmart.security;

import com.shopsmart.common.api.ErrorResponse;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes the standard error envelope from filters, where controller advice does not apply. */
@Component
public class SecurityErrorWriter {

    private final JsonMapper jsonMapper;

    public SecurityErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, ErrorCode code,
                      String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ErrorResponse body = new ErrorResponse(false, message, code.name(), Instant.now(), request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY), List.of(), Map.of());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
