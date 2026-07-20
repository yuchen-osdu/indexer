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

package org.opengroup.osdu.indexer.util;

import co.elastic.clients.elasticsearch._types.ErrorCause;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Utility methods for interpreting Elasticsearch bulk response errors.
 *
 * <p>Uses the typed {@code ErrorCause.type()} field from the {@code co.elastic.clients} Java API
 * client rather than substring-matching against {@code ErrorCause.reason()}, which was the
 * legacy behaviour of the High-Level REST Client and caused the parsing-exception fallback to
 * silently stop working after the ES 8.x rename of {@code mapper_parsing_exception} to
 * {@code document_parsing_exception}.
 */
public final class ElasticsearchBulkErrorUtils {

    public static final String METRIC_PARSING_EXCEPTION_FALLBACK = "osdu.indexer.parsing_exception.fallback";

    /** ES 7.x / legacy Elasticsearch exception type for mapping/parsing errors. */
    public static final String MAPPER_PARSING_EXCEPTION = "mapper_parsing_exception";

    /** ES 8.x renamed exception type (was {@code mapper_parsing_exception} in ES 7.x). */
    public static final String DOCUMENT_PARSING_EXCEPTION = "document_parsing_exception";

    static final int MAX_REASON_LENGTH = 512;

    private ElasticsearchBulkErrorUtils() {}

    /**
     * Returns true if the given {@link ErrorCause} represents a mapping/document parsing
     * exception that should trigger a fallback re-index without data fields.
     */
    public static boolean isParsingException(ErrorCause error) {
        if (error == null || error.type() == null) return false;
        return MAPPER_PARSING_EXCEPTION.equals(error.type())
            || DOCUMENT_PARSING_EXCEPTION.equals(error.type());
    }

    /**
     * Builds a human-readable error reason string from an {@link ErrorCause}.
     *
     * <p>Includes the error type and reason in bracket notation to preserve compatibility
     * with existing Geneva monitors and KQL alert rules that pattern-match on
     * {@code type=<exceptionType>} in job status traces.
     */
    public static String buildErrorReason(ErrorCause error) {
        if (error == null) return "Unknown error";
        String type = error.type();
        String reason = error.reason() != null ? error.reason() : "Unknown error";
        String result = (type != null) ? "[type=" + type + ", reason=" + reason + "]" : reason;
        if (error.causedBy() != null && error.causedBy().reason() != null) {
            ErrorCause causedBy = error.causedBy();
            String causedByStr = causedBy.type() != null
                    ? "[type=" + causedBy.type() + ", reason=" + causedBy.reason() + "]"
                    : causedBy.reason();
            result = result + " | caused by: " + causedByStr;
        }
        if (result.length() > MAX_REASON_LENGTH) {
            result = result.substring(0, MAX_REASON_LENGTH) + "...";
        }
        return result;
    }

    /**
     * Increments the parsing-exception fallback counter.
     *
     * @param meterRegistry   the meter registry; no-op when {@code null}
     * @param dataPartitionId the data-partition-id tag value
     * @param exceptionType   the raw ES exception type string from {@code ErrorCause.type()}
     * @param amount          number of records to add to the counter
     */
    public static void incrementParsingExceptionFallbackCounter(
            MeterRegistry meterRegistry, String dataPartitionId, String exceptionType, double amount) {
        if (meterRegistry == null || amount <= 0) return;
        String safeType = DOCUMENT_PARSING_EXCEPTION.equals(exceptionType)
                ? DOCUMENT_PARSING_EXCEPTION : MAPPER_PARSING_EXCEPTION;
        Counter.builder(METRIC_PARSING_EXCEPTION_FALLBACK)
                .description("Count of records queued for fallback re-index due to parsing exception")
                .tag("data_partition_id", dataPartitionId != null ? dataPartitionId : "unknown")
                .tag("exception_type", safeType)
                .register(meterRegistry)
                .increment(amount);
    }
}
