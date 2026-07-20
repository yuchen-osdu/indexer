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

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.model.search.SearchServiceRole;
import org.opengroup.osdu.core.common.model.storage.validation.ValidKind;
import org.opengroup.osdu.core.common.search.ElasticIndexNameResolver;
import org.opengroup.osdu.indexer.SwaggerDoc;
import org.opengroup.osdu.indexer.logging.AuditLogger;
import org.opengroup.osdu.indexer.service.IndexerService;
import org.opengroup.osdu.indexer.service.IndicesServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

@Log
@RestController
@RequestScope
@Tag(name = "cleanup-indicies-api", description = "Cleanup Indicies API")
public class CleanupIndiciesApi {

  @Autowired
  private IndexerService indexerService;

  @Autowired
  private AuditLogger auditLogger;

  @Inject
  private ElasticIndexNameResolver elasticIndexNameResolver;

  @Inject
  private IndicesServiceImpl indicesService;

  private static final String ENTITLEMENT_GROUP = "users.datalake.ops";

  @Operation(hidden = true)
  @PostMapping(path = "/index-cleanup", consumes = "application/json")
  @PreAuthorize("@authorizationFilter.hasPermission('" + SearchServiceRole.ADMIN + "')")
  public ResponseEntity cleanupIndices(@NotNull(message = SwaggerDoc.REQUEST_VALIDATION_NOT_NULL_BODY)
                                         @Valid @RequestBody RecordChangedMessages message) {
    if (message == null) {
      throw new AppException(HttpStatus.BAD_REQUEST.value(), "Request body is null",
          SwaggerDoc.REQUEST_VALIDATION_NOT_NULL_BODY);
    }

    if (message.missingAccountId()) {
      throw new AppException(HttpStatus.BAD_REQUEST.value(), "Invalid tenant",
          String.format("Required header: '%s' not found", DpsHeaders.DATA_PARTITION_ID));
    }
    try {
      Type listType = new TypeToken<List<RecordInfo>>() {
      }.getType();
      List<RecordInfo> recordInfos = new Gson().fromJson(message.getData(), listType);

      if (recordInfos.isEmpty()) {
        log.info("none of record-change message can be deserialized");
        return new ResponseEntity(HttpStatus.OK);
      }
      indexerService.processSchemaMessages(recordInfos);

      auditLogger.getIndexCleanUpJobRun(recordInfos.stream()
              .map(RecordInfo::getKind)
              .collect(Collectors.toList()));
      return new ResponseEntity(HttpStatus.OK);
    } catch (AppException e) {
      throw e;
    } catch (JsonParseException e) {
      throw new AppException(HttpStatus.BAD_REQUEST.value(), "Request payload parsing error", "Unable to parse request payload.", e);
    } catch (Exception e) {
      throw new AppException(HttpStatus.BAD_REQUEST.value(), "Unknown error", "An unknown error has occurred.", e);
    }
  }

  @Operation(summary = "${cleanupIndiciesApi.deleteIndex.summary}", description = "${cleanupIndiciesApi.deleteIndex.description}",
          security = {@SecurityRequirement(name = "Authorization")}, tags = { "cleanup-indicies-api" })
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
  @DeleteMapping(value = "/index", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("@authorizationFilter.hasPermission('" + ENTITLEMENT_GROUP + "')")
  public ResponseEntity deleteIndex(@Parameter(description = "Kind", example = "tenant1:public:well:1.0.2")
                                      @RequestParam("kind") @NotBlank @ValidKind String kind) {
    String index = elasticIndexNameResolver.getIndexNameFromKind(kind);
    try {
      boolean responseStatus = indicesService.deleteIndex(index);
      if (responseStatus) {
        this.auditLogger.indexDeleteSuccess(singletonList(index));
      }
      return new ResponseEntity(HttpStatus.OK);
    } catch (AppException e) {
      throw e;
    } catch (Exception e) {
      this.auditLogger.indexDeleteFail(singletonList(index));
      throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unknown error", "An unknown error has occurred.", e);
    }
  }
}
