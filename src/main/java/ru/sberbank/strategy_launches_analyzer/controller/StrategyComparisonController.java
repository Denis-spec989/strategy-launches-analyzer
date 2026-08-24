package ru.sberbank.strategy_launches_analyzer.controller;

import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import ru.sberbank.strategy_launches_analyzer.dto.api.ErrorResponse;
import ru.sberbank.strategy_launches_analyzer.service.CompareStrategyLaunchesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/strategies", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Strategy comparison")
public class StrategyComparisonController {
    private final CompareStrategyLaunchesUseCase compareStrategyLaunchesUseCase;

    @PostMapping(path = "/compare", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Compare main and shadow strategy launches")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Comparison completed; deterministic results are returned even if LLM analysis failed.",
                    content = @Content(schema = @Schema(implementation = CompareStrategyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public CompareStrategyResponse compare(@Valid @RequestBody CompareStrategyRequest request) {
        return compareStrategyLaunchesUseCase.compare(request);
    }
}
