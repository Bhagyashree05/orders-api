package com.omni.orders.api.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String message, List<String> details,
                             Instant timestamp, String traceId) {
    public static ErrorResponse of(String error, String message, String traceId) {
        return new ErrorResponse(error, message, null, Instant.now(), traceId);
    }
    public static ErrorResponse of(String error, String message, List<String> details, String traceId) {
        return new ErrorResponse(error, message, details, Instant.now(), traceId);
    }
}
