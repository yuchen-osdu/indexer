// Copyright © Schlumberger
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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.indexer.logging.AuditLogger;
import org.opengroup.osdu.indexer.service.IClusterConfigurationService;
import org.opengroup.osdu.indexer.service.IndexAliasService;
import org.opengroup.osdu.indexer.util.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

import java.io.IOException;

import static java.util.Collections.singletonList;
import static org.opengroup.osdu.core.common.model.http.DpsHeaders.DATA_PARTITION_ID;

@RestController
@RequestMapping("/partitions")
@RequestScope
@Tag(name = "partition-setup-api", description = "Partition Setup API")
public class PartitionSetupApi {

    @Autowired
    private IndexAliasService indexAliasService;
    @Autowired
    private IClusterConfigurationService clusterConfigurationService;
    @Autowired
    private JaxRsDpsLog jaxRsDpsLog;
    @Autowired
    private AuditLogger auditLogger;

    @Operation(summary = "${partitionSetupApi.provisionPartition.summary}", description = "${partitionSetupApi.provisionPartition.description}",
            security = {@SecurityRequirement(name = "Authorization")}, tags = {"partition-setup-api"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "403", description = "User not authorized to perform the action", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "404", description = "Not Found", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "502", description = "Bad Gateway", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "503", description = "Service Unavailable", content = {@Content(schema = @Schema(implementation = AppError.class))})
    })
    @PreAuthorize("@authorizationFilter.hasPermission('" + Role.USER_OPS + "')")
    @PutMapping(path = "/provision", consumes = "application/json")
    public ResponseEntity<?> provisionPartition(@RequestHeader(DATA_PARTITION_ID) String dataPartitionId) throws IOException {
        this.jaxRsDpsLog.info("applying cluster configuration for partition: " + dataPartitionId);
        this.clusterConfigurationService.updateClusterConfiguration();
        this.jaxRsDpsLog.info("creating default alias for all pre-exiting indices for partition: " + dataPartitionId);
        this.indexAliasService.createIndexAliasesForAll();
        this.auditLogger.getConfigurePartition(singletonList(dataPartitionId));
        return new ResponseEntity<>(org.springframework.http.HttpStatus.OK);
    }
}
