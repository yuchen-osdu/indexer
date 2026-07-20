/*
 *    Copyright (c) 2024. EPAM Systems, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.opengroup.osdu.indexer.integTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.COLLABORATIONS_FEATURE_NAME;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.ElasticsearchClusterClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opengroup.osdu.core.common.http.IUrlFetchService;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.IndexProgress;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.indexer.RecordStatus;
import org.opengroup.osdu.core.common.model.search.RecordChangedMessages;
import org.opengroup.osdu.core.common.partition.IPartitionFactory;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticCredentialsCache;
import org.opengroup.osdu.core.common.provider.interfaces.IElasticRepository;
import org.opengroup.osdu.core.common.provider.interfaces.IIndexCache;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.indexer.cache.partitionsafe.SchemaCache;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.indexer.provider.interfaces.IPublisher;
import org.opengroup.osdu.indexer.provider.interfaces.ISchemaCache;
import org.opengroup.osdu.indexer.service.IndexerMappingServiceImpl;
import org.opengroup.osdu.indexer.service.IndicesService;
import org.opengroup.osdu.indexer.service.ReindexService;
import org.opengroup.osdu.indexer.service.SchemaEventsProcessor;
import org.opengroup.osdu.indexer.util.ElasticClientHandler;
import org.opengroup.osdu.indexer.util.FeatureFlagStateConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * This test is not intended to be integration or unit.
 * The reason it was created to speed up a develop process without the need to start the whole application.
 * */
@EnableWebMvc
@SpringBootTest(
    properties = {"LOG_PREFIX=indexer",
        "featureFlag.strategy=appProperty",
        "featureFlag.asIngestedCoordinates.enabled=true",
        "featureFlag.keywordLower.enabled=true",
        "featureFlag.bagOfWords.enabled=true"
    }
)
@AutoConfigureMockMvc(addFilters = false)
@ComponentScan(value = {"org.opengroup.osdu", "org.opengroup.osdu.indexer", "org.opengroup.osdu.is"})
public class RecordIndexerApiTest {

  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private IndexerConfigurationProperties configurationProperties;
  @MockBean
  private IRequestInfo iRequestInfo;
  @MockBean
  private ReindexService reIndexService;
  @MockBean
  private SchemaEventsProcessor eventsProcessingService;
  @MockBean
  private ISchemaCache iSchemaCache;
  @MockBean
  private SchemaCache schemaCache;
  @MockBean
  private IIndexCache iIndexCache;
  @MockBean
  private IElasticRepository IElasticRepository;
  @MockBean
  private IElasticCredentialsCache IElasticCredentialsCache;
  @MockBean
  private IServiceAccountJwtClient IServiceAccountJwtClient;
  @MockBean
  private IPublisher IPublisher;
  @MockBean
  private DpsHeaders dpsHeaders;
  @MockBean
  private IUrlFetchService iUrlFetchService;
  @MockBean
  private ElasticClientHandler elasticClientHandler;
  @MockBean
  private IndicesService indicesService;
  @MockBean
  private ElasticsearchClient restHighLevelClient;
  @MockBean
  private ElasticsearchIndicesClient indicesClient;
  @MockBean
  private ElasticsearchClusterClient clusterClient;
  @SpyBean
  private IndexerMappingServiceImpl indexerMappingServiceImpl;
  @MockBean
  private BulkResponseItem bulkItemResponse;
  @MockBean
  private JobStatus jobStatus;
  @MockBean
  private IPartitionFactory iPartitionFactory;
  @MockBean
  private IPartitionProvider iPartitionProvider;
  @MockBean
  private FeatureFlagStateConfiguration stateConfiguration;

  @ParameterizedTest
  @CsvSource({"true,true", "true,false", "false,false", "false, true"})
  public void givenXcollaborationHeaderAndFeatureEnabledTest(String isFeatureEnabled,
                                                             String shouldIncludeXattributeIntoHeader)
      throws Exception {

    // set env to enable feature
    System.setProperty(COLLABORATIONS_FEATURE_NAME, isFeatureEnabled);

    // constants
    String schemaAsJsonFile = "src/test/java/org/opengroup/osdu/indexer/integTest/fixtures/schema.json";
    String recordsAsJsonFile = "src/test/java/org/opengroup/osdu/indexer/integTest/fixtures/records.json";
    String id = "osdu:CollaborationProjectCollection:12345678";

    // given
    Map<String, Object> dataSchema = new HashMap<>();
    dataSchema.put("dataSchema-key", "dataSchema-value");

    // create List of RecordInfos for RecordChangedMessages
    String kind = "osdu:wks:CollaborationProjectCollection:1.0.0";
    RecordInfo recordInfo = RecordInfo.builder()
        .id(id)
        .kind(kind)
        .op(OperationType.create.toString())
        .build();
    List<RecordInfo> recordInfos = List.of(recordInfo);
    String data = objectMapper.writeValueAsString(recordInfos);

    // create record change message for request's payload
    Map<String, String> attributes = new HashMap<>();
    attributes.put("data-partition-id", "test");
    String nameSpaceAsUUID = UUID.randomUUID().toString();
    String xCollaborationValue = "id=" + nameSpaceAsUUID + ",application=app-name-in-x-collab-header";
    if (Boolean.parseBoolean(shouldIncludeXattributeIntoHeader)) {
      attributes.put(DpsHeaders.COLLABORATION, xCollaborationValue);
    }

    RecordChangedMessages recordChangesMessage = RecordChangedMessages.builder()
        .attributes(attributes)
        .data(data)
        .build();
    String recordMessage = objectMapper.writeValueAsString(recordChangesMessage);

    // to accept http request, configuration, headers
    when(iUrlFetchService.sendRequest(any())).thenReturn(createHttpResponse(recordsAsJsonFile));
    when(iRequestInfo.getHeaders()).thenReturn(dpsHeaders);
    when(dpsHeaders.getUserEmail()).thenReturn("user-name-from-dpsHeaders");
    when(configurationProperties.getStorageRecordsBatchSize()).thenReturn(5);

    // through the logic we will ask for a real schema to check if we need to add new fields into index
    when(schemaCache.get(kind)).thenReturn(getJsonAsStringFromJsonFile(schemaAsJsonFile));
    when(elasticClientHandler.createRestClient(any())).thenReturn(restHighLevelClient);
    when(elasticClientHandler.createRestClientFromClusterInfo()).thenReturn(restHighLevelClient);
    when(restHighLevelClient.indices()).thenReturn(indicesClient);
    when(indicesService.isIndexReady(any(), eq(""))).thenReturn(true);
    when(indicesService.createIndex(any(), any(), any(), any())).thenReturn(true);
    BooleanResponse booleanResponse = new BooleanResponse(true);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(booleanResponse);
    when(restHighLevelClient.cluster()).thenReturn(clusterClient);

    // looking for mappings in ES
    GetMappingResponse mappingsResponse = new GetMappingResponse.Builder().build();
    when(indicesClient.getMapping()).thenReturn(mappingsResponse);
    doReturn(true).when(indexerMappingServiceImpl).isTypeExist(any(), any(), any());

    // put mapping in ES
    PutMappingResponse putMappingResponse = new PutMappingResponse.Builder()
        .acknowledged(true)
        .build();
    when(indicesClient.putMapping(any(PutMappingRequest.class))).thenReturn(putMappingResponse);

    // bulk result we receive when put mapping in ES
    BulkResponseItem[] bulkItemResponses = new BulkResponseItem[2];
    bulkItemResponses[0] = bulkItemResponse;
    bulkItemResponses[1] = bulkItemResponse;
    when(bulkItemResponse.id()).thenReturn("id01");
    BulkResponse bulkResponse = new BulkResponse.Builder()
        .took(2L)
        .errors(false)
        .items(List.of(bulkItemResponse))
        .build();
    when(restHighLevelClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);


    // to pass log audit block successfully had to mock jobStatus
    RecordStatus recordStatus = RecordStatus.builder()
        .status(IndexingStatus.SUCCESS)
        .indexProgress(IndexProgress.builder()
                           .statusCode(0)
                           .build())
        .build();
    when(jobStatus.getJobStatusByRecordId(any())).thenReturn(recordStatus);
    when(jobStatus.getIdsByValidUpsertIndexingStatus()).thenReturn(List.of(id));

    // do the test
    this.mockMvc.perform(post("/_dps/task-handlers/index-worker")
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(recordMessage)
        )
        .andExpect(status().isOk());
  }

  private org.opengroup.osdu.core.common.model.http.HttpResponse createHttpResponse(String fileName) {
    org.opengroup.osdu.core.common.model.http.HttpResponse httpResponse = new org.opengroup.osdu.core.common.model.http.HttpResponse();
    httpResponse.setBody(getJsonAsStringFromJsonFile(fileName));
    httpResponse.setResponseCode(200);
    return httpResponse;
  }

  private String getJsonAsStringFromJsonFile(String relativePathToFile) {
    String jsonString = null;
    try {
      JsonNode jsonNode = objectMapper.readValue(Paths.get(relativePathToFile).toFile(), JsonNode.class);
      jsonString = objectMapper.writeValueAsString(jsonNode);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return jsonString;
  }

}
