// Copyright 2017-2019, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.indexer.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.core.common.model.indexer.Records;
import org.opengroup.osdu.core.common.model.search.SearchServiceRole;
import org.opengroup.osdu.indexer.logging.AuditLogger;
import org.opengroup.osdu.indexer.model.ReindexRecordsRequest;
import org.opengroup.osdu.indexer.model.ReindexRecordsResponse;
import org.opengroup.osdu.indexer.service.IndexSchemaService;
import org.opengroup.osdu.indexer.service.ReindexService;
import org.opengroup.osdu.indexer.util.Role;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

@RestController
@RequestMapping("/reindex")
@RequestScope
@Tag(name = "reindex-api", description = "Reindex API")
public class ReindexApi {

    @Inject
    private ReindexService reIndexService;
    @Inject
    private IndexSchemaService indexSchemaService;
    @Inject
    private AuditLogger auditLogger;

    @Operation(summary = "${reindexApi.reindexRecords.summary}", description = "${reindexApi.reindexRecords.description}",
            security = {@SecurityRequirement(name = "Authorization")}, tags = { "reindex-api" })
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted"),
            @ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "403", description = "User not authorized to perform the action",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "404", description = "Not Found",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class))})
    })
    @PreAuthorize("@authorizationFilter.hasPermission('" + SearchServiceRole.ADMIN + "')")
    @PostMapping(path = "/records", consumes = "application/json")
    public ResponseEntity<?> reindexRecords(
            @NotNull @Valid @RequestBody ReindexRecordsRequest reindexRecordsRequest) {
        Records records = this.reIndexService.reindexRecords(reindexRecordsRequest.getRecordIds());
        List<String> reindexedRecords = records.getRecords().stream().map(Records.Entity::getId).collect(Collectors.toList());
        if (!reindexedRecords.isEmpty()) {
            this.auditLogger.getReindexRecords(reindexedRecords);
        }
        return new ResponseEntity<>(ReindexRecordsResponse.builder().reIndexedRecords(records.getRecords().stream().map(Records.Entity::getId).collect(Collectors.toList())).notFoundRecords(records.getNotFound()).build(), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "${reindexApi.reindex.summary}", description = "${reindexApi.reindex.description}",
            security = {@SecurityRequirement(name = "Authorization")}, tags = { "reindex-api" })
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "403", description = "User not authorized to perform the action",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "404", description = "Not Found",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class))})
    })
    @PreAuthorize("@authorizationFilter.hasPermission('" + SearchServiceRole.ADMIN + "')")
    @PostMapping
    public ResponseEntity<?> reindex(@NotNull @Valid @RequestBody RecordReindexRequest recordReindexRequest,
                                     @Parameter(description = "Force Clean")
            @RequestParam(value = "force_clean", defaultValue = "false") boolean forceClean) throws IOException {
        this.reIndexService.reindexKind(recordReindexRequest, this.indexSchemaService.isStorageSchemaSyncRequired(recordReindexRequest.getKind(), forceClean), true);
        this.auditLogger.getReindex(singletonList(recordReindexRequest.getKind()));
        return new ResponseEntity<>(org.springframework.http.HttpStatus.OK);
    }

    @Operation(summary = "${reindexApi.fullReindex.summary}", description = "${reindexApi.fullReindex.description}",
            security = {@SecurityRequirement(name = "Authorization")}, tags = { "reindex-api" })
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "403", description = "User not authorized to perform the action",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "404", description = "Not Found",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class))})
    })
    @PreAuthorize("@authorizationFilter.hasPermission('" + Role.USER_OPS + "')")
    @PatchMapping
    public ResponseEntity<String> fullReindex( @Parameter(description = "Force Clean")
            @RequestParam(value = "force_clean", defaultValue = "false") boolean forceClean) throws IOException {
        this.reIndexService.fullReindex(forceClean);
        return new ResponseEntity<>(org.springframework.http.HttpStatus.OK);
    }
}
