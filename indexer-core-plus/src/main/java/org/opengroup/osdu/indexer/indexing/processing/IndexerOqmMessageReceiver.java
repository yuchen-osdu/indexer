/*
 *  Copyright 2020-2023 Google LLC
 *  Copyright 2020-2023 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.indexer.indexing.processing;

import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.opengroup.osdu.oqm.core.model.OqmAckReplier;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.oqm.core.model.OqmMessageReceiver;
import org.opengroup.osdu.indexer.indexing.scope.ThreadDpsHeaders;
import org.opengroup.osdu.indexer.indexing.thread.ThreadScopeContextHolder;

import jakarta.validation.constraints.NotNull;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public abstract class IndexerOqmMessageReceiver implements OqmMessageReceiver {

    protected final ThreadDpsHeaders dpsHeaders;
    private final TokenProvider tokenProvider;

  /**
   * Receives and processes an OQM message, emulating the behavior of the previous IndexQueue service and message bus.
   * Validates the input payload and handles exceptions appropriately for error processing precision.
   *
   * @param oqmMessage the OQM message to be processed
   * @param oqmAckReplier the ack replier for acknowledging message receipt
   */
    @Override
    public void receiveMessage(OqmMessage oqmMessage, OqmAckReplier oqmAckReplier) {
        log.info("OQM message: {} - {} - {}", oqmMessage.getId(), oqmMessage.getData(), oqmMessage.getAttributes());
        if (!validInput(oqmMessage)) {
            log.error("Not valid event payload, event will not be processed.");
            oqmAckReplier.nack(false);
            return;
        }

        try {
            DpsHeaders headers = getHeaders(oqmMessage);
            // Filling thread context required by the core services.
            dpsHeaders.setThreadContext(headers.getHeaders());
            sendMessage(oqmMessage);
            oqmAckReplier.ack();
        } catch (AppException appException) {
            if (isExceptionToSkip(appException)) {
                skipMessage(oqmMessage, dpsHeaders, oqmAckReplier, appException);
            } else {
                rescheduleMessage(oqmMessage, dpsHeaders, oqmAckReplier, getException(appException));
            }
        } catch (SchemaProcessingException schemaException) {
            log.warn("Event ID: {}. Schema processing failed, message will be skipped without rescheduling: {}",
                oqmMessage.getId(), schemaException.getMessage());
            oqmAckReplier.nack(false);
        } catch (Exception exception) {
            rescheduleMessage(oqmMessage, dpsHeaders, oqmAckReplier, exception);
        } catch (Throwable e) {
            //Catching throwable is necessary, otherwise, errors like NoSuchMethodError will slip through silently.
            log.error(
                "HALT! Unrecoverable malfunction! Unexpected error was thrown during processing! Event ID: "
                    + oqmMessage.getId() + ". Correlation ID: " + dpsHeaders.getCorrelationId(), e);
            throw e;
        } finally {
            // Cleaning thread context after processing is finished and the thread dies out.
            ThreadScopeContextHolder.removeThreadScopeAttributes();
        }
    }

    /**
     * Determines whether an exception should be skipped during processing or not.
     * Exceptions within the specified range of status codes are considered skippable.
     * Additionally, SchemaProcessingException is marked as skippable, as retrying it is ineffective.
     *
     * @param exception the exception thrown during processing
     * @return true if the exception should be skipped, false otherwise
     */
    private static boolean isExceptionToSkip(AppException exception) {
        int statusCode = exception.getError().getCode();
        Exception originalException = getException(exception);
        return statusCode > 199 && statusCode < 300 || originalException instanceof SchemaProcessingException;
    }

    private static void skipMessage(OqmMessage oqmMessage, DpsHeaders dpsHeaders,
        OqmAckReplier oqmAckReplier, AppException appException) {
        log.info(
            "Event ID: " + oqmMessage.getId() + ". Correlation ID: " + dpsHeaders.getCorrelationId()
                + ", was not processed, and will NOT be rescheduled.", appException);
        oqmAckReplier.nack(false);
    }

    private static void rescheduleMessage(OqmMessage oqmMessage, DpsHeaders dpsHeaders,
        OqmAckReplier oqmAckReplier, Exception exception) {
        log.error(
            "Event id : " + oqmMessage.getId() + ". Correlation ID: " + dpsHeaders.getCorrelationId()
                + ", was not processed, and will BE rescheduled.", exception);
        oqmAckReplier.nack(true);
    }

    /**
     * It is possible to get both AppException that wraps original Exception
     * or the original Exception without any wrapper.
     */
    @NotNull
    private static Exception getException(AppException appException) {
        return Optional.ofNullable(appException.getOriginalException()).orElse(appException);
    }

    private boolean validInput(OqmMessage oqmMessage) {
        boolean isValid = true;
        if (Strings.isNullOrEmpty(oqmMessage.getData()) || oqmMessage.getData().equals("{}")) {
            log.error("Message body is empty, message id: {}, attributes: {}", oqmMessage.getId(), oqmMessage.getAttributes());
            isValid = false;
        }
        if (oqmMessage.getAttributes() == null || oqmMessage.getAttributes().size() == 0) {
            log.error("Attribute map not found, message id: {}, attributes: {}", oqmMessage.getId(), oqmMessage.getAttributes());
            isValid = false;
        }
        return isValid;
    }

  /**
   * Sends a message to the respective API endpoint. This method is abstract and should be implemented
   * according to the specific requirements of the endpoint.
   *
   * @param oqmMessage the message to be sent
   */
    protected abstract void sendMessage(OqmMessage oqmMessage) throws Exception;

    @NotNull
    private DpsHeaders getHeaders(OqmMessage oqmMessage) {
        DpsHeaders headers = DpsHeaders.createFromMap(oqmMessage.getAttributes());
        headers.getHeaders().put("authorization", "Bearer " + tokenProvider.getIdToken());
        return headers;
    }
}
