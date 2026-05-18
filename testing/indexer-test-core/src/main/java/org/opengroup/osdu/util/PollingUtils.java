/*
 * Copyright 2017-2025, The Open Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opengroup.osdu.util;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for polling operations with retry and backoff strategies.
 */
public class PollingUtils {
    private static final Logger log = Logger.getLogger(PollingUtils.class.getName());

    private PollingUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Core polling method with retry and backoff.
     *
     * @param config           Configuration for polling behavior
     * @param operation        The operation to perform on each attempt
     * @param successCondition Predicate to determine if the operation succeeded
     * @param onAttempt        Optional callback for each attempt (can be null)
     * @param <T>              Type of the result
     * @return PollingResult containing the outcome
     */
    public static <T> PollingResult<T> pollWithRetry(
            PollingConfig config,
            Supplier<T> operation,
            Predicate<T> successCondition,
            Consumer<PollingContext> onAttempt) throws InterruptedException {

        long startTime = System.currentTimeMillis();
        int waitIndex = 0;

        for (int attempt = 0; attempt < config.getMaxAttempts(); attempt++) {
            long elapsedTime = System.currentTimeMillis() - startTime;

            // Check timeout
            if (config.getMaxWaitTimeMs() > 0 && elapsedTime > config.getMaxWaitTimeMs()) {
                log.info(String.format("%s: Timeout after %d attempts in %.1f seconds",
                    config.getDescription(), attempt, elapsedTime / 1000.0));
                return PollingResult.timeout(attempt, elapsedTime);
            }

            // Provide context for callbacks
            PollingContext context = new PollingContext(attempt, elapsedTime, config);

            // Execute pre-attempt callback
            if (onAttempt != null) {
                onAttempt.accept(context);
            }

            // Skip operation if context indicates to skip
            if (context.isSkipOperation()) {
                if (attempt < config.getMaxAttempts() - 1) {
                    long waitTime = config.getWaitTime(waitIndex++);
                    log.info(String.format("%s: Skipping attempt %d, waiting %dms",
                        config.getDescription(), attempt + 1, waitTime));
                    Thread.sleep(waitTime);
                }
                continue;
            }

            // Perform the operation
            try {
                T result = operation.get();

                // Check success condition
                if (successCondition.test(result)) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.info(String.format("%s: Success after %d attempts in %.1f seconds",
                        config.getDescription(), attempt + 1, totalTime / 1000.0));
                    return PollingResult.success(result, attempt + 1, totalTime);
                }

                // Log progress if we have a partial result
                if (result != null) {
                    log.info(String.format("%s: Attempt %d - Current result: %s",
                        config.getDescription(), attempt + 1, result));
                }

            } catch (Exception e) {
                log.log(Level.WARNING, String.format("%s: Exception on attempt %d",
                    config.getDescription(), attempt + 1), e);
            }

            // Wait before next attempt (except for the last attempt)
            if (attempt < config.getMaxAttempts() - 1) {
                long waitTime = config.getWaitTime(waitIndex++);
                log.info(String.format("%s: Attempt %d failed, waiting %dms before retry",
                    config.getDescription(), attempt + 1, waitTime));
                Thread.sleep(waitTime);
            }
        }

        // Max attempts exhausted
        long totalTime = System.currentTimeMillis() - startTime;
        return PollingResult.failure(
            String.format("%s failed after %d attempts in %.1f seconds",
                config.getDescription(), config.getMaxAttempts(), totalTime / 1000.0),
            config.getMaxAttempts(),
            totalTime
        );
    }

    /**
     * Simple retry operation without return value.
     */
    public static PollingResult<Void> retryOperation(
            PollingConfig config,
            BooleanSupplier operation) throws InterruptedException {

        return pollWithRetry(
            config,
            () -> {
                boolean success = operation.getAsBoolean();
                return success ? Boolean.TRUE : null;
            },
            result -> result != null && result,
            null
        ).isSuccess() ? PollingResult.success(null, 0, 0) : PollingResult.failure("Operation failed", 0, 0);
    }

    /**
     * Poll for index to be ready (specialized for Elasticsearch).
     */
    public static PollingResult<Boolean> pollIndexReady(
            String indexName,
            PollingConfig config,
            Function<String, Boolean> healthCheckFunction) throws InterruptedException {

        return pollWithRetry(
            config,
            () -> {
                try {
                    return healthCheckFunction.apply(indexName);
                } catch (Exception e) {
                    log.warning(String.format("Health check failed for index %s: %s", indexName, e.getMessage()));
                    return false;
                }
            },
            result -> result != null && result,
            null
        );
    }

    /**
     * Poll for documents to appear in an index.
     */
    public static PollingResult<Long> pollForDocuments(
            String indexName,
            PollingConfig config,
            ElasticUtils elasticUtils) throws InterruptedException {

        final boolean[] healthChecked = {false};

        return pollWithRetry(
            config,
            () -> {
                try {
                    return elasticUtils.fetchRecords(indexName);
                } catch (IOException e) {
                    log.warning(String.format("Failed to fetch records from %s: %s", indexName, e.getMessage()));
                    return 0L;
                }
            },
            count -> count > 0,
            context -> {
                // Health check logic
                if (config.shouldPerformHealthCheck(context.attempt) && !healthChecked[0]) {
                    try {
                        if (elasticUtils.waitForIndexGreen(indexName, 10)) {
                            healthChecked[0] = true;
                            log.info(String.format("Index '%s' health check passed", indexName));
                        } else {
                            log.info(String.format("Index '%s' not ready at attempt %d", indexName, context.attempt + 1));
                            context.skipOperation();
                        }
                    } catch (IOException e) {
                        log.warning(String.format("Health check error for %s: %s", indexName, e.getMessage()));
                        context.skipOperation();
                    }
                }

                // Refresh logic
                if (config.shouldPerformRefresh(context.attempt) && context.attempt > 0) {
                    try {
                        elasticUtils.refreshIndex(indexName);
                        log.info(String.format("Refreshed index: %s at attempt %d", indexName, context.attempt + 1));
                    } catch (IOException e) {
                        log.warning(String.format("Failed to refresh index %s: %s", indexName, e.getMessage()));
                    }
                }
            }
        );
    }

    /**
     * Poll for alias to be ready and pointing to expected physical index.
     */
    public static PollingResult<Boolean> pollForAliasReady(
            String aliasName,
            String expectedPhysicalIndex,
            PollingConfig config,
            ElasticUtils elasticUtils) throws InterruptedException {

        return pollWithRetry(
            config,
            () -> {
                try {
                    // Check if alias exists
                    if (!elasticUtils.aliasExists(aliasName)) {
                        return false;
                    }

                    // If expected physical index specified, verify it points to it
                    if (expectedPhysicalIndex != null && !expectedPhysicalIndex.isEmpty()) {
                        String actualPhysicalIndex = elasticUtils.getPhysicalIndexFromAlias(aliasName);
                        return expectedPhysicalIndex.equals(actualPhysicalIndex);
                    }

                    // Just alias existence check
                    return true;
                } catch (Exception e) {
                    log.warning(String.format("Failed to check alias %s: %s", aliasName, e.getMessage()));
                    return false;
                }
            },
            result -> result != null && result,
            context -> {
                // Perform health check on attempt 0 and 3
                if (config.shouldPerformHealthCheck(context.attempt)) {
                    log.info(String.format("Checking alias '%s' readiness at attempt %d", aliasName, context.attempt + 1));
                }
            }
        );
    }

    /**
     * Poll for physical index to exist.
     */
    public static PollingResult<Boolean> pollForPhysicalIndexReady(
            String indexName,
            PollingConfig config,
            ElasticUtils elasticUtils) throws InterruptedException {

        return pollWithRetry(
            config,
            () -> {
                try {
                    return elasticUtils.physicalIndexExists(indexName);
                } catch (Exception e) {
                    log.warning(String.format("Failed to check physical index %s: %s", indexName, e.getMessage()));
                    return false;
                }
            },
            result -> result != null && result,
            context -> {
                if (config.shouldPerformHealthCheck(context.attempt)) {
                    log.info(String.format("Checking physical index '%s' existence at attempt %d", indexName, context.attempt + 1));
                }
            }
        );
    }

    /**
     * Poll for a specific number of documents in an index.
     */
    public static PollingResult<Long> pollForExpectedCount(
            String indexName,
            long expectedCount,
            PollingConfig config,
            ElasticUtils elasticUtils) throws InterruptedException {

        final boolean[] healthChecked = {false};
        final long[] lastCount = {0};

        return pollWithRetry(
            config,
            () -> {
                try {
                    long count = elasticUtils.fetchRecords(indexName);
                    lastCount[0] = count;
                    return count;
                } catch (IOException e) {
                    log.warning(String.format("Failed to fetch records from %s: %s", indexName, e.getMessage()));
                    return lastCount[0];
                }
            },
            count -> count == expectedCount,
            context -> {
                // Health check logic
                if (config.shouldPerformHealthCheck(context.attempt) && !healthChecked[0]) {
                    try {
                        if (elasticUtils.waitForIndexGreen(indexName, 10)) {
                            healthChecked[0] = true;
                            log.info(String.format("Index '%s' health check passed", indexName));
                        } else {
                            log.info(String.format("Index '%s' not ready at attempt %d", indexName, context.attempt + 1));
                            context.skipOperation();
                        }
                    } catch (IOException e) {
                        log.warning(String.format("Health check error for %s: %s", indexName, e.getMessage()));
                        context.skipOperation();
                    }
                }

                // More aggressive refresh when close to target
                boolean closeToTarget = lastCount[0] > 0 &&
                    Math.abs(expectedCount - lastCount[0]) <= 2;

                if ((config.shouldPerformRefresh(context.attempt) || closeToTarget) && context.attempt > 0) {
                    try {
                        elasticUtils.refreshIndex(indexName);
                        log.info(String.format("Refreshed index: %s at attempt %d (current: %d, expected: %d)",
                            indexName, context.attempt + 1, lastCount[0], expectedCount));
                    } catch (IOException e) {
                        log.warning(String.format("Failed to refresh index %s: %s", indexName, e.getMessage()));
                    }
                }
            }
        );
    }

    /**
     * Poll for specified fields to appear in an index mapping.
     */
    public static PollingResult<Boolean> pollForMappingFields(
            String indexName,
            String[] fieldNames,
            PollingConfig config,
            ElasticUtils elasticUtils) throws InterruptedException {

        return pollWithRetry(
            config,
            () -> {
                try {
                    return elasticUtils.checkMappingFieldsExist(indexName, fieldNames);
                } catch (Exception e) {
                    log.warning(String.format("Failed to check mapping fields for %s: %s", indexName, e.getMessage()));
                    return false;
                }
            },
            result -> result != null && result,
            context -> {
                if (config.shouldPerformHealthCheck(context.attempt)) {
                    log.info(String.format("Checking mapping fields for '%s' at attempt %d", indexName, context.attempt + 1));
                }
            }
        );
    }

    /**
     * Poll for documents matching a specific attribute value in an index.
     * Returns the list of matching documents when found.
     */
    public static PollingResult<java.util.List<java.util.Map<String, Object>>> pollForDocumentByAttribute(
            String indexName,
            String attributePath,
            String expectedValue,
            PollingConfig config,
            ElasticUtils elasticUtils) throws InterruptedException {

        return pollWithRetry(
            config,
            () -> {
                try {
                    elasticUtils.refreshIndex(indexName);
                    return elasticUtils.fetchRecordsByAttribute(indexName, attributePath, expectedValue);
                } catch (Exception e) {
                    log.warning(String.format("Failed to fetch records by attribute %s=%s from %s: %s",
                        attributePath, expectedValue, indexName, e.getMessage()));
                    return null;
                }
            },
            records -> records != null && !records.isEmpty(),
            context -> {
                if (config.shouldPerformHealthCheck(context.attempt)) {
                    log.info(String.format("Checking for document with %s=%s in '%s' at attempt %d",
                        attributePath, expectedValue, indexName, context.attempt + 1));
                }
            }
        );
    }

    /**
     * Poll for document count to match between two indexes.
     * Returns true when both indexes have the same document count.
     */
    public static PollingResult<Long> pollForMatchingDocumentCount(
            String index1,
            String index2,
            PollingConfig config,
            ElasticUtils elasticUtils) throws InterruptedException {

        return pollWithRetry(
            config,
            () -> {
                try {
                    elasticUtils.refreshIndex(index1);
                    elasticUtils.refreshIndex(index2);
                    long count1 = elasticUtils.fetchRecords(index1);
                    long count2 = elasticUtils.fetchRecords(index2);
                    if (count1 == count2 && count1 > 0) {
                        return count1;
                    }
                    log.info(String.format("Document count mismatch: %s has %d, %s has %d",
                        index1, count1, index2, count2));
                    return -1L;
                } catch (Exception e) {
                    log.warning(String.format("Failed to compare document counts between %s and %s: %s",
                        index1, index2, e.getMessage()));
                    return -1L;
                }
            },
            count -> count != null && count > 0,
            context -> {
                if (config.shouldPerformHealthCheck(context.attempt)) {
                    log.info(String.format("Comparing document counts between '%s' and '%s' at attempt %d",
                        index1, index2, context.attempt + 1));
                }
            }
        );
    }

    /**
     * Poll for document to be deleted from an index (document count decreases or specific document not found).
     */
    public static PollingResult<Boolean> pollForDocumentDeletion(
            String indexName,
            long expectedCountAfterDeletion,
            PollingConfig config,
            ElasticUtils elasticUtils) throws InterruptedException {

        return pollWithRetry(
            config,
            () -> {
                try {
                    elasticUtils.refreshIndex(indexName);
                    long currentCount = elasticUtils.fetchRecords(indexName);
                    log.info(String.format("Current document count in %s: %d (expecting: %d)",
                        indexName, currentCount, expectedCountAfterDeletion));
                    return currentCount == expectedCountAfterDeletion;
                } catch (Exception e) {
                    log.warning(String.format("Failed to check document count in %s: %s", indexName, e.getMessage()));
                    return false;
                }
            },
            result -> result != null && result,
            context -> {
                if (config.shouldPerformHealthCheck(context.attempt)) {
                    log.info(String.format("Checking document deletion in '%s' at attempt %d", indexName, context.attempt + 1));
                }
            }
        );
    }

    /**
     * Poll a query function until it returns the expected document count, refreshing the index
     * between attempts so newly-indexed documents become visible to the query.
     *
     * <p>On the first health-check attempt the helper waits for the index to be ready (matching
     * {@link #pollForExpectedCount}). This is important for queries against augmenter-generated
     * fields (e.g. {@code bagOfWords}, virtual properties) which require the index to be fully
     * established before the augmenter populates them.
     *
     * <p>Uses {@link PollingConfig#documentPollingWithExpectedCount()}; for a different timeout
     * profile call the overload that accepts a {@link PollingConfig}.
     *
     * @param indexName     Index to refresh between attempts
     * @param expectedCount Count the query should return
     * @param queryCount    Supplier wrapping the actual fetch* call; should return -1L on exception
     * @param elasticUtils  Used for periodic index refresh between polls and for the index-ready check
     */
    public static PollingResult<Long> pollForQueryResultCount(
            String indexName,
            long expectedCount,
            Supplier<Long> queryCount,
            ElasticUtils elasticUtils) throws InterruptedException {

        return pollForQueryResultCount(indexName, expectedCount, queryCount, elasticUtils,
            PollingConfig.documentPollingWithExpectedCount());
    }

    /**
     * As {@link #pollForQueryResultCount(String, long, Supplier, ElasticUtils)} but with a
     * caller-supplied {@link PollingConfig}. Use this when the default
     * {@code documentPollingWithExpectedCount} timeout/backoff is not appropriate (e.g. a known-fast
     * scenario that should fail faster on regression).
     */
    public static PollingResult<Long> pollForQueryResultCount(
            String indexName,
            long expectedCount,
            Supplier<Long> queryCount,
            ElasticUtils elasticUtils,
            PollingConfig config) throws InterruptedException {

        final boolean[] healthChecked = {false};

        return pollWithRetry(
            config,
            queryCount,
            count -> count != null && count == expectedCount,
            context -> {
                // Wait for the index to be ready before querying the first time.
                // Without this, queries against augmenter-generated fields (bagOfWords,
                // virtual properties) can race the augmenter and return 0 indefinitely.
                if (config.shouldPerformHealthCheck(context.getAttempt()) && !healthChecked[0]) {
                    try {
                        if (elasticUtils.waitForIndexGreen(indexName, 10)) {
                            healthChecked[0] = true;
                        } else {
                            log.info(String.format("Index '%s' not ready at attempt %d",
                                indexName, context.getAttempt() + 1));
                            context.skipOperation();
                        }
                    } catch (IOException e) {
                        log.warning(String.format("Health check error for %s: %s",
                            indexName, e.getMessage()));
                        context.skipOperation();
                    }
                }

                // Periodic refresh so newly-augmented documents become visible to subsequent queries.
                if (config.shouldPerformRefresh(context.getAttempt()) && context.getAttempt() > 0) {
                    try {
                        elasticUtils.refreshIndex(indexName);
                    } catch (IOException e) {
                        log.warning(String.format("Failed to refresh index %s: %s",
                            indexName, e.getMessage()));
                    }
                }
            }
        );
    }

    /**
     * Context object passed to callbacks during polling.
     */
    public static class PollingContext {
        private final int attempt;
        private final long elapsedTimeMs;
        private final PollingConfig config;
        private boolean skipOperation = false;

        public PollingContext(int attempt, long elapsedTimeMs, PollingConfig config) {
            this.attempt = attempt;
            this.elapsedTimeMs = elapsedTimeMs;
            this.config = config;
        }

        public int getAttempt() {
            return attempt;
        }

        public long getElapsedTimeMs() {
            return elapsedTimeMs;
        }

        public PollingConfig getConfig() {
            return config;
        }

        public void skipOperation() {
            this.skipOperation = true;
        }

        public boolean isSkipOperation() {
            return skipOperation;
        }
    }
}