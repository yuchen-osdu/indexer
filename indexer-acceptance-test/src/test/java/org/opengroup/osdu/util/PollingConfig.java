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

import java.util.Arrays;

/**
 * Configuration for polling operations with retry and backoff strategies.
 */
public class PollingConfig {
    private final int maxAttempts;
    private final long maxWaitTimeMs;
    private final long[] waitTimes;
    private final boolean enableHealthCheck;
    private final int[] healthCheckAttempts;
    private final boolean enableRefresh;
    private final int[] refreshAttempts;
    private final String description;

    private PollingConfig(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.maxWaitTimeMs = builder.maxWaitTimeMs;
        this.waitTimes = builder.waitTimes;
        this.enableHealthCheck = builder.enableHealthCheck;
        this.healthCheckAttempts = builder.healthCheckAttempts;
        this.enableRefresh = builder.enableRefresh;
        this.refreshAttempts = builder.refreshAttempts;
        this.description = builder.description;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getMaxWaitTimeMs() {
        return maxWaitTimeMs;
    }

    public long[] getWaitTimes() {
        return waitTimes;
    }

    public long getWaitTime(int attemptIndex) {
        if (waitTimes == null || waitTimes.length == 0) {
            return 0;
        }
        return attemptIndex < waitTimes.length ? waitTimes[attemptIndex] : waitTimes[waitTimes.length - 1];
    }

    public boolean isEnableHealthCheck() {
        return enableHealthCheck;
    }

    public boolean shouldPerformHealthCheck(int attempt) {
        if (!enableHealthCheck || healthCheckAttempts == null) {
            return false;
        }
        return Arrays.stream(healthCheckAttempts).anyMatch(a -> a == attempt);
    }

    public boolean isEnableRefresh() {
        return enableRefresh;
    }

    public boolean shouldPerformRefresh(int attempt) {
        if (!enableRefresh || refreshAttempts == null) {
            return false;
        }
        return Arrays.stream(refreshAttempts).anyMatch(a -> a == attempt);
    }

    public String getDescription() {
        return description;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxAttempts = 30;
        private long maxWaitTimeMs = 300000; // 5 minutes default
        private long[] waitTimes = {1000, 2000, 3000, 5000, 8000, 10000, 15000}; // Default increasing backoff
        private boolean enableHealthCheck = false;
        private int[] healthCheckAttempts = {0, 3, 6};
        private boolean enableRefresh = false;
        private int[] refreshAttempts = {1, 2, 3, 6, 9, 12, 15, 18, 21, 24, 27};
        private String description = "Polling operation";

        public Builder withMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder withMaxWaitTime(long maxWaitTimeMs) {
            this.maxWaitTimeMs = maxWaitTimeMs;
            return this;
        }

        public Builder withWaitTimes(long... waitTimes) {
            this.waitTimes = waitTimes;
            return this;
        }

        public Builder withIncreasingBackoff() {
            this.waitTimes = new long[]{1000, 2000, 3000, 5000, 8000, 10000, 15000};
            return this;
        }

        public Builder withLinearBackoff(long baseMs, int steps) {
            this.waitTimes = new long[steps];
            for (int i = 0; i < steps; i++) {
                this.waitTimes[i] = baseMs * (i + 1);
            }
            return this;
        }

        public Builder withFixedDelay(long delayMs) {
            this.waitTimes = new long[]{delayMs};
            return this;
        }

        public Builder withHealthChecks(int... attempts) {
            this.enableHealthCheck = true;
            this.healthCheckAttempts = attempts;
            return this;
        }

        public Builder withRefreshAttempts(int... attempts) {
            this.enableRefresh = true;
            this.refreshAttempts = attempts;
            return this;
        }

        public Builder withEarlyAndPeriodicRefresh() {
            this.enableRefresh = true;
            this.refreshAttempts = new int[]{1, 2, 3, 6, 9, 12, 15, 18, 21, 24, 27};
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public PollingConfig build() {
            return new PollingConfig(this);
        }
    }

    // Predefined configurations for common use cases
    public static PollingConfig indexCreation() {
        return builder()
            .withMaxAttempts(8)
            .withWaitTimes(2000, 5000, 10000, 15000, 15000, 15000, 15000, 15000)
            .withDescription("Index creation")
            .build();
    }

    public static PollingConfig indexDeletion() {
        return builder()
            .withMaxAttempts(8)
            .withWaitTimes(3000, 6000, 9000, 12000, 12000, 12000, 12000, 12000)
            .withDescription("Index deletion")
            .build();
    }

    public static PollingConfig documentPolling() {
        return builder()
            .withMaxAttempts(30)
            .withMaxWaitTime(300000)
            .withIncreasingBackoff()
            .withHealthChecks(0, 3, 6)
            .withEarlyAndPeriodicRefresh()
            .withDescription("Document polling")
            .build();
    }

    public static PollingConfig documentPollingWithExpectedCount() {
        return builder()
            .withMaxAttempts(30)
            .withMaxWaitTime(300000)
            .withIncreasingBackoff()
            .withHealthChecks(0, 3, 6)
            .withEarlyAndPeriodicRefresh()
            .withDescription("Document polling with expected count")
            .build();
    }

    public static PollingConfig aliasCreation() {
        return builder()
            .withMaxAttempts(10)
            .withMaxWaitTime(30000) // 30 seconds for alias creation
            .withWaitTimes(500, 1000, 2000, 3000, 5000)
            .withHealthChecks(0, 3)
            .withDescription("Alias creation")
            .build();
    }

    public static PollingConfig mappingFieldsVerification() {
        return builder()
            .withMaxAttempts(10)
            .withMaxWaitTime(30000) // 30 seconds for mapping fields verification
            .withWaitTimes(500, 1000, 2000, 3000, 5000)
            .withDescription("Mapping fields verification")
            .build();
    }

    /**
     * Configuration for dual-write polling operations.
     * Uses moderate retry count with exponential backoff suitable for
     * verifying documents are written to both primary and secondary indexes.
     */
    public static PollingConfig dualWritePolling() {
        return builder()
            .withMaxAttempts(15)
            .withMaxWaitTime(60000) // 60 seconds max for dual-write verification
            .withWaitTimes(1000, 2000, 2000, 3000, 3000, 5000, 5000)
            .withHealthChecks(0, 3, 6)
            .withDescription("Dual-write document polling")
            .build();
    }

    /**
     * Configuration for verifying document deletion propagation.
     * Allows time for delete operations to propagate through the indexer.
     */
    public static PollingConfig documentDeletion() {
        return builder()
            .withMaxAttempts(10)
            .withMaxWaitTime(30000) // 30 seconds for deletion verification
            .withWaitTimes(1000, 2000, 3000, 5000, 5000)
            .withHealthChecks(0, 3)
            .withDescription("Document deletion verification")
            .build();
    }

    /**
     * Configuration for verifying document update propagation.
     * Slightly shorter timeouts for updates vs deletes.
     */
    public static PollingConfig documentUpdate() {
        return builder()
            .withMaxAttempts(8)
            .withMaxWaitTime(20000) // 20 seconds for update verification
            .withWaitTimes(500, 1000, 2000, 3000, 5000)
            .withHealthChecks(0, 3)
            .withDescription("Document update verification")
            .build();
    }
}
