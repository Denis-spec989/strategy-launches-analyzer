package com.github.denisspec989.strategy_launches_analyzer.controller;

import com.github.denisspec989.strategy_launches_analyzer.dto.api.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsUnexpectedExceptionToInternalErrorContract() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        assertThat(response.getBody().message()).isEqualTo("Internal error.");
        assertThat(response.getBody().message()).doesNotContain("boom");
    }
}
