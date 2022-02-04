package dev.crashteam.styx.util;

import dev.crashteam.styx.exception.OriginalRequestException;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public class ErrorUtils {

    public static Mono<ResponseEntity<Object>> getOriginalErrorResponse(OriginalRequestException e) {
        return Mono.just(ErrorResponse.builder()
                        .code(e.getStatusCode())
                        .message(e.getMessage()))
                .map(response ->
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
    }

    public static Mono<ResponseEntity<Object>> getOriginalErrorResponse(Throwable e) {
        return Mono.just(ErrorResponse.builder()
                        .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .message("Retries exhausted: " + e.getMessage()))
                .map(response ->
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
    }

    @Data
    @Builder
    public static class ErrorResponse {
        private int code;
        private String message;
    }
}
