package ru.sberbank.strategy_launches_analyzer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sberbank.strategy_launches_analyzer.dto.api.ErrorResponse;
import ru.sberbank.strategy_launches_analyzer.service.batch.BatchArchive;
import ru.sberbank.strategy_launches_analyzer.service.batch.BatchCompareStrategiesUseCase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/v1/strategies")
@RequiredArgsConstructor
@Tag(name = "Strategy comparison")
@Slf4j
public class BatchStrategyComparisonController {
    public static final String NDJSON_MEDIA_TYPE = "application/x-ndjson";
    public static final String ZIP_MEDIA_TYPE = "application/zip";

    private final BatchCompareStrategiesUseCase batchCompareStrategiesUseCase;

    @PostMapping(path = "/compare/batch", consumes = NDJSON_MEDIA_TYPE, produces = ZIP_MEDIA_TYPE)
    @Operation(
            summary = "Compare up to 1000 main and shadow strategy launch pairs",
            description = "Each non-empty UTF-8 NDJSON line must conform to CompareStrategyRequest. "
                    + "The ZIP response contains manifest.json, report.xlsx, and results.ndjson.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = NDJSON_MEDIA_TYPE,
                            schema = @Schema(type = "string", description = "CompareStrategyRequest objects, one per line")
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Batch comparison archive created. Item failures are contained in the archive.",
                    headers = {
                            @Header(
                                    name = "X-Batch-Id",
                                    description = "Server-generated batch UUID",
                                    schema = @Schema(type = "string", format = "uuid")
                            ),
                            @Header(
                                    name = HttpHeaders.CONTENT_DISPOSITION,
                                    description = "Attachment filename strategy-comparison-<batchId>.zip",
                                    schema = @Schema(type = "string")
                            )
                    },
                    content = @Content(
                            mediaType = ZIP_MEDIA_TYPE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Batch is empty.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Batch size limit exceeded.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Another batch is active on this instance.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Batch processing timed out.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "507", description = "Insufficient temporary storage.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void compareBatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (BatchArchive archive = batchCompareStrategiesUseCase.compare(
                request.getInputStream(),
                request.getContentLengthLong()
        )) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(ZIP_MEDIA_TYPE);
            response.setContentLengthLong(archive.sizeBytes());
            response.setHeader("X-Batch-Id", archive.batchId().toString());
            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"strategy-comparison-" + archive.batchId() + ".zip\""
            );
            try (InputStream archiveInput = Files.newInputStream(archive.archivePath())) {
                archiveInput.transferTo(response.getOutputStream());
                response.flushBuffer();
            }
            log.info("Batch comparison archive delivered: batchId={}, sizeBytes={}",
                    archive.batchId(), archive.sizeBytes());
        }
    }
}
