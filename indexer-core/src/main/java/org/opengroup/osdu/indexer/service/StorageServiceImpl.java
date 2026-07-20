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

package org.opengroup.osdu.indexer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpMethods;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.opengroup.osdu.core.common.http.FetchServiceHttpRequest;
import org.opengroup.osdu.core.common.http.IHttpClientHandler;
import org.opengroup.osdu.core.common.http.IUrlFetchService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.HttpResponse;
import org.opengroup.osdu.core.common.model.http.RequestStatus;
import org.opengroup.osdu.core.common.model.indexer.IndexingStatus;
import org.opengroup.osdu.core.common.model.indexer.JobStatus;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.indexer.RecordQueryResponse;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.core.common.model.indexer.Records;
import org.opengroup.osdu.core.common.model.search.RecordMetaAttribute;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.opengroup.osdu.core.common.model.storage.RecordIds;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.indexer.model.XcollaborationHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.opengroup.osdu.core.common.Constants.SLB_FRAME_OF_REFERENCE_VALUE;
import static org.opengroup.osdu.core.common.model.http.DpsHeaders.FRAME_OF_REFERENCE;

@Slf4j
@Component
public class StorageServiceImpl implements StorageService {

    private final Gson gson = new Gson();

    @Value("${storage.retry.maxAttempts:5}")
    int maxRetryAttempts;

    @Value("${storage.retry.baseDelayMs:1000}")
    long baseDelayMs;

    @Value("${storage.retry.maxDelayMs:60000}")
    long maxDelayMs;

    @Value("${storage.retry.jitterFactor:0.5}")
    double jitterFactor;

    @Inject
    private ObjectMapper objectMapper;
    @Inject
    private IUrlFetchService urlFetchService;
    @Inject
    private IHttpClientHandler httpClientHandler;
    @Inject
    private JobStatus jobStatus;
    @Inject
    private IRequestInfo requestInfo;
    @Inject
    private JaxRsDpsLog jaxRsDpsLog;
    @Inject
    private IndexerConfigurationProperties configurationProperties;
    @Autowired
    private XcollaborationHolder xCollaborationHolder;

    @Override
    public Records getStorageRecords(List<String> ids, List<RecordInfo> recordChangedInfos) throws AppException, URISyntaxException {
        List<Records.Entity> valid = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        List<String> missingRetryRecordIds = new ArrayList<>();
        Map<String, String> recordChangedMap = recordChangedInfos.stream().collect(Collectors.toMap(RecordInfo::getId, RecordInfo::getKind, (a, b) -> b));
        Map<String, String> validRecordKindPatchMap = getValidRecordKindPatchMap(recordChangedInfos);

        List<List<String>> batch = Lists.partition(ids, configurationProperties.getStorageRecordsBatchSize());
        for (List<String> recordsBatch : batch) {
            Records storageOut = this.getRecords(recordsBatch, recordChangedMap, validRecordKindPatchMap);
            valid.addAll(storageOut.getRecords());
            notFound.addAll(storageOut.getNotFound());
            conversionStatuses.addAll(storageOut.getConversionStatuses());
            missingRetryRecordIds.addAll(storageOut.getMissingRetryRecords());
        }
        return Records.builder().records(valid).notFound(notFound).conversionStatuses(conversionStatuses).missingRetryRecords(missingRetryRecordIds).build();
    }

    @Override
    public Records getStorageRecords(List<String> ids) throws URISyntaxException {
        List<Records.Entity> valid = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        List<String> missingRetryRecordIds = new ArrayList<>();

        List<List<String>> batch = Lists.partition(ids, configurationProperties.getStorageRecordsBatchSize());
        for (List<String> recordsBatch : batch) {
            Records storageOut = this.getRecords(recordsBatch);
            valid.addAll(storageOut.getRecords());
            notFound.addAll(storageOut.getNotFound());
            conversionStatuses.addAll(storageOut.getConversionStatuses());
            missingRetryRecordIds.addAll(storageOut.getMissingRetryRecords());
        }
        return Records.builder().records(valid).notFound(notFound).conversionStatuses(conversionStatuses).missingRetryRecords(missingRetryRecordIds).build();
    }

    protected Records getRecords(List<String> ids, Map<String, String> recordChangedMap, Map<String, String> validRecordKindPatchMap) throws URISyntaxException {
        // e.g. {"records":["test:10"]}
        String body = this.gson.toJson(RecordIds.builder().records(ids).build());

        DpsHeaders headers = this.requestInfo.getHeaders();
        if (xCollaborationHolder.isFeatureEnabledAndHeaderExists()) {
            headers.put(DpsHeaders.COLLABORATION, xCollaborationHolder.getXCollaborationHeader());
        }
        headers.put(FRAME_OF_REFERENCE, SLB_FRAME_OF_REFERENCE_VALUE);
        FetchServiceHttpRequest request = FetchServiceHttpRequest
                .builder()
                .httpMethod(HttpMethods.POST)
                .url(configurationProperties.getStorageQueryRecordForConversionHost())
                .headers(headers)
                .body(body).build();
        HttpResponse response = this.urlFetchService.sendRequest(request);
        log.debug("Is isFeatureEnabledAndHeaderExists: {}", xCollaborationHolder.isFeatureEnabledAndHeaderExists());
        return this.validateStorageResponse(response, ids, recordChangedMap, validRecordKindPatchMap);
    }

    protected Records getRecords(List<String> ids) throws URISyntaxException {
        String body = this.gson.toJson(RecordIds.builder().records(ids).build());
        DpsHeaders headers = this.requestInfo.getHeaders();
        URIBuilder builder = new URIBuilder(configurationProperties.getStorageQueryRecordHost());
        HttpPost request = new HttpPost(builder.build());
        request.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
        // we do not need retry on storage based on not found record
        HttpResponse response = httpClientHandler.sendRequest(request, headers);
        if (response.getResponseCode() > 299) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Error", response.getBody());
        }
        try {
            Records records = this.objectMapper.readValue(response.getBody(), Records.class);
            List<String> notFoundRecordIds = new ArrayList<>(ids);
            notFoundRecordIds.removeAll(records.getRecords().stream().map(Records.Entity::getId).collect(Collectors.toList()));
            records.setNotFound(notFoundRecordIds);
            return records;
        } catch (JsonProcessingException e) {
            throw new AppException(RequestStatus.INVALID_RECORD, "Invalid request", "Successful Storage service response with wrong json", e);
        }
    }

    private Records validateStorageResponse(HttpResponse response, List<String> ids, Map<String, String> recordChangedMap, Map<String, String> validRecordKindPatchMap) {
        String bulkStorageData = response.getBody();

        // retry entire payload -- storage service returned empty response
        if (Strings.isNullOrEmpty(bulkStorageData)) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Invalid request", "Storage service returned empty response");
        }

        if (response.getResponseCode() == 500) {
            throw new AppException(RequestStatus.NO_RETRY, "Server error", String.format("Storage service error: %s", response.getBody()));
        }

        Records records = null;
        try {
            records = this.objectMapper.readValue(bulkStorageData, Records.class);
        } catch (JsonProcessingException e) {
            throw new AppException(RequestStatus.INVALID_RECORD, "Invalid request", "Successful Storage service response with wrong json", e);
        }

        // no retry possible, update record status as failed -- storage service cannot locate records
        if (!records.getNotFound().isEmpty()) {
            jaxRsDpsLog.error(records.getNotFound().size() + " records were not found. Full list: " + records.getNotFound());
            this.jobStatus.addOrUpdateRecordStatus(records.getNotFound(), IndexingStatus.FAIL, RequestStatus.INVALID_RECORD, "Storage service records not found", String.format("Storage service records not found: %s", String.join(",", records.getNotFound())));
        }

        List<Records.Entity> validRecords = records.getRecords();
        if (validRecords.isEmpty()) {
            // no need to retry, ack the CloudTask message -- nothing to process from RecordChangeMessage batch
            if (response.isSuccessCode()) {
                throw new AppException(RequestStatus.INVALID_RECORD, "Invalid request", "Successful Storage service response with no valid records");
            }

            // retry entire payload -- storage service returned empty valid records with non-success response-code
            jaxRsDpsLog.warning(String.format("unable to proceed, valid storage record not found. | upstream response code: %s | record ids: %s", response.getResponseCode(), String.join(" | ", ids)));
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Invalid request", "Storage service error");
        }

        // validate kind to avoid data duplication
        List<String> staleRecords = getStaleRecordsUpdate(recordChangedMap, validRecordKindPatchMap, validRecords);
        List<Records.Entity> indexableRecords = validateKind(validRecords, staleRecords);
        records.setRecords(indexableRecords);

        Map<String, List<String>> conversionStatus = getConversionErrors(records.getConversionStatuses());
        for (Records.Entity storageRecord : indexableRecords) {
            String recordId = storageRecord.getId();
            if (conversionStatus.get(recordId) == null) {
                continue;
            }
            for (String status : conversionStatus.get(recordId)) {
                this.jobStatus.addOrUpdateRecordStatus(recordId, IndexingStatus.WARN, HttpStatus.SC_BAD_REQUEST, status, String.format("record-id: %s | %s", recordId, status));
            }
        }

        // retry missing records -- storage did not return response for all RecordChangeMessage record-ids
        if ((records.getRecords().size() + records.getNotFound().size() + staleRecords.size()) != ids.size()) {
            List<String> missingRecords = this.getMissingRecords(records, ids, staleRecords);
            records.setMissingRetryRecords(missingRecords);
            this.jobStatus.addOrUpdateRecordStatus(missingRecords, IndexingStatus.FAIL, HttpStatus.SC_NOT_FOUND, "Partial response received from Storage service - missing records", String.format("Partial response received from Storage service: %s", String.join(",", missingRecords)));
        }

        return records;
    }

    private List<String> getMissingRecords(Records records, List<String> ids, List<String> staleRecords) {
        List<String> validRecordIds = records.getRecords().stream().map(Records.Entity::getId).collect(Collectors.toList());
        List<String> invalidRecordsIds = records.getNotFound();
        List<String> requestedIds = new ArrayList<>(ids);
        requestedIds.removeAll(validRecordIds);
        requestedIds.removeAll(invalidRecordsIds);
        requestedIds.removeAll(staleRecords);
        return requestedIds;
    }

    private Map<String, List<String>> getConversionErrors(List<ConversionStatus> conversionStatuses) {
        Map<String, List<String>> errorsByRecordId = new HashMap<>();
        for (ConversionStatus conversionStatus : conversionStatuses) {
            if (Strings.isNullOrEmpty(conversionStatus.getStatus())) continue;
            if (conversionStatus.getStatus().equalsIgnoreCase("ERROR")) {
                List<String> statuses = errorsByRecordId.getOrDefault(conversionStatus.getId(), new LinkedList<>());
                statuses.addAll(conversionStatus.getErrors());
                errorsByRecordId.put(conversionStatus.getId(), statuses);
            }
        }
        return errorsByRecordId;
    }

    private List<Records.Entity> validateKind(List<Records.Entity> validRecords, List<String> staleRecords) {
        List<Records.Entity> indexableRecords = new ArrayList<>();
        if (!staleRecords.isEmpty()) {
            for (Records.Entity record : validRecords) {
                if (staleRecords.contains(record.getId())) {
                    continue;
                }
                indexableRecords.add(record);
            }
            jaxRsDpsLog.warning(String.format("stale records found with older kind, skipping indexing | record ids: %s", String.join(" | ", staleRecords)));
        } else {
            indexableRecords.addAll(validRecords);
        }
        return indexableRecords;
    }

    private List<String> getStaleRecordsUpdate(Map<String, String> recordChangedMap, Map<String, String> validRecordKindPatchMap, List<Records.Entity> validRecords) {
        List<String> staleRecords = new ArrayList<>();
        for (Records.Entity storageRecord : validRecords) {
            String kindOnStorage = storageRecord.getKind();
            String kindOnMessage = recordChangedMap.get(storageRecord.getId());
            if (validRecordKindPatchMap.containsKey(storageRecord.getId())) {
                continue;
            }
            if (!kindOnStorage.equals(kindOnMessage)) {
                staleRecords.add(storageRecord.getId());
            }
        }
        return staleRecords;
    }

    /*
    * Gets valid  kind patch map, previousVersionKind is included on the record update message in such cases
    * */
    private Map<String, String> getValidRecordKindPatchMap(List<RecordInfo> recordChangedInfos) {
        Map<String, String> out = new HashMap<>();
        for (RecordInfo msg : recordChangedInfos) {
            OperationType op = OperationType.valueOf(msg.getOp());
            if (op != OperationType.update) {
                continue;
            }
            if (!Strings.isNullOrEmpty(msg.getPreviousVersionKind())) {
                out.put(msg.getId(), msg.getPreviousVersionKind());
            }
        }
        return out;
    }

    @Override
    public RecordQueryResponse getRecordsByKind(RecordReindexRequest reindexRequest) throws URISyntaxException {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(RecordMetaAttribute.KIND.getValue(), reindexRequest.getKind());
        queryParams.put("limit", configurationProperties.getStorageRecordsByKindBatchSize().toString());
        if (!Strings.isNullOrEmpty(reindexRequest.getCursor())) {
            queryParams.put("cursor", reindexRequest.getCursor());
        }

        if (requestInfo == null)
            throw new AppException(HttpStatus.SC_NO_CONTENT, "Invalid header", "header can't be null");

        FetchServiceHttpRequest request = FetchServiceHttpRequest.builder()
                .httpMethod(HttpMethods.GET)
                .headers(this.requestInfo.getHeadersMap())
                .url(configurationProperties.getStorageQueryRecordHost())
                .queryParams(queryParams)
                .build();

        return executeWithRetry(request, reindexRequest.getKind());
    }

    /**
     * Executes a storage query request with retry logic using exponential backoff and jitter.
     * Retries on transient errors (429 Too Many Requests, 500) up to {@code maxRetryAttempts} times.
     * Non-retryable errors (4xx other than 429) are thrown immediately.
     */
    private RecordQueryResponse executeWithRetry(FetchServiceHttpRequest request, String kind) throws URISyntaxException {
        AppException lastException = null;
        String sanitizedKind = sanitize(kind);

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            HttpResponse response = this.urlFetchService.sendRequest(request);

            if (response.isSuccessCode()) {
                if (attempt > 1) {
                    jaxRsDpsLog.info(String.format("Storage query for kind %s succeeded on attempt %d after %d retries",
                            sanitizedKind, attempt, attempt - 1));
                }
                return this.gson.fromJson(response.getBody(), RecordQueryResponse.class);
            }

            String reason = response.getResponseCode() == 429 ? "Too Many Requests" : "Storage query error";
            String body = response.getBody();
            String truncatedBody = sanitize((body != null && body.length() > 500) ? body.substring(0, 500) + "..." : body);
            AppException appException = new AppException(response.getResponseCode(), reason,
                    String.format("Storage service returned HTTP %d on cursor query for kind %s: %s",
                            response.getResponseCode(), sanitizedKind, truncatedBody));

            if (!isRetryableError(response.getResponseCode())) {
                throw appException;
            }

            lastException = appException;

            if (attempt == maxRetryAttempts) {
                jaxRsDpsLog.error(String.format("Max retry attempts (%d) exhausted for kind %s. Last error: HTTP %d",
                        maxRetryAttempts, sanitizedKind, response.getResponseCode()));
                break;
            }

            long delayMs = calculateBackoffWithJitter(attempt);
            jaxRsDpsLog.warning(String.format("Storage service returned retryable error for kind %s. " +
                            "Attempt %d/%d failed with HTTP %d. Retrying in %d ms. Error: %s",
                    sanitizedKind, attempt, maxRetryAttempts, response.getResponseCode(), delayMs, truncatedBody));

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "Reindex interrupted",
                        "The storage query was interrupted during retry backoff.", ie);
            }
        }

        throw new AppException(HttpStatus.SC_SERVICE_UNAVAILABLE,
                "Storage service unavailable",
                String.format("Failed to query records for kind %s after %d attempts. " +
                        "The storage service may be experiencing high load. Please retry later.",
                        sanitizedKind, maxRetryAttempts),
                lastException);
    }

    /**
     * Determines if an HTTP status code represents a retryable error.
     * 503 is intentionally excluded — it is handled by provider-level retry mechanisms.
     */
    private boolean isRetryableError(int statusCode) {
        return statusCode == 429 ||
               statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR;
    }

    /**
     * Calculates exponential backoff delay with jitter.
     * Formula: min(maxDelayMs, baseDelayMs * 2^(attempt-1)) * (1 + random(-jitterFactor, +jitterFactor))
     */
    private long calculateBackoffWithJitter(int attempt) {
        long exponentialDelay = baseDelayMs * (1L << (attempt - 1));
        long cappedDelay = Math.min(exponentialDelay, maxDelayMs);
        double jitter = 1.0 + (ThreadLocalRandom.current().nextDouble() * 2 * jitterFactor - jitterFactor);
        return (long) (cappedDelay * jitter);
    }

    /** Strips control characters (except CR, LF, TAB) from input to prevent log injection. */
    private String sanitize(String input) {
        if (input == null) return null;
        return input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
    }

    @Override
    public String getStorageSchema(String kind) throws URISyntaxException, UnsupportedEncodingException {
        String url = String.format("%s/%s", configurationProperties.getStorageSchemaHost(), URLEncoder.encode(kind, StandardCharsets.UTF_8.toString()));
        FetchServiceHttpRequest request = FetchServiceHttpRequest.builder()
                .httpMethod(HttpMethods.GET)
                .headers(this.requestInfo.getHeadersMap())
                .url(url)
                .build();
        HttpResponse response = this.urlFetchService.sendRequest(request);
        return response.getResponseCode() != HttpStatus.SC_OK ? null : response.getBody();
    }

    @Override
    public List<String> getAllKinds() throws URISyntaxException {
        String url = configurationProperties.getStorageQueryKindsHost();
        FetchServiceHttpRequest request = FetchServiceHttpRequest.builder()
            .httpMethod(HttpMethods.GET)
            .headers(this.requestInfo.getHeadersMap())
            .url(url)
            .build();
        HttpResponse response = this.urlFetchService.sendRequest(request);
        JsonObject asJsonObject = new JsonParser().parse(response.getBody()).getAsJsonObject();
        JsonElement results = asJsonObject.get("results");
        return response.getResponseCode() != HttpStatus.SC_OK ? null : this.gson.fromJson(results, List.class);
    }
}
