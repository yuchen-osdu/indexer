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

/**
 * Represents the result of a polling operation with retry.
 *
 * @param <T> The type of value returned by the polling operation
 */
public class PollingResult<T> {
    private final T value;
    private final boolean success;
    private final int attempts;
    private final long totalWaitTimeMs;
    private final String failureReason;

    private PollingResult(T value, boolean success, int attempts, long totalWaitTimeMs, String failureReason) {
        this.value = value;
        this.success = success;
        this.attempts = attempts;
        this.totalWaitTimeMs = totalWaitTimeMs;
        this.failureReason = failureReason;
    }

    public static <T> PollingResult<T> success(T value, int attempts, long totalWaitTimeMs) {
        return new PollingResult<>(value, true, attempts, totalWaitTimeMs, null);
    }

    public static <T> PollingResult<T> failure(String reason, int attempts, long totalWaitTimeMs) {
        return new PollingResult<>(null, false, attempts, totalWaitTimeMs, reason);
    }

    public static <T> PollingResult<T> timeout(int attempts, long totalWaitTimeMs) {
        return new PollingResult<>(null, false, attempts, totalWaitTimeMs, "Timeout exceeded");
    }

    public T getValue() {
        return value;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getAttempts() {
        return attempts;
    }

    public long getTotalWaitTimeMs() {
        return totalWaitTimeMs;
    }

    public double getTotalWaitTimeSeconds() {
        return totalWaitTimeMs / 1000.0;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public T getValueOrThrow() {
        if (!success) {
            throw new AssertionError(failureReason != null ? failureReason : "Polling operation failed");
        }
        return value;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("PollingResult[success=true, value=%s, attempts=%d, totalWaitTime=%.1fs]",
                value, attempts, getTotalWaitTimeSeconds());
        } else {
            return String.format("PollingResult[success=false, reason=%s, attempts=%d, totalWaitTime=%.1fs]",
                failureReason, attempts, getTotalWaitTimeSeconds());
        }
    }
}