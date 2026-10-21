//  Copyright © Microsoft Corporation
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.opengroup.osdu.indexer.error;

import jakarta.validation.ValidationException;

import javassist.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.opengroup.osdu.core.common.model.http.AppException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE - 1)
@ControllerAdvice
public class GlobalExceptionMapperCore extends ResponseEntityExceptionHandler {

    @Autowired
    private JaxRsDpsLog jaxRsDpsLogger;

    @ExceptionHandler(AppException.class)
    protected ResponseEntity<Object> handleAppException(AppException e) {
        return this.getErrorResponse(e);
    }

    @ExceptionHandler(ValidationException.class)
    protected ResponseEntity<Object> handleValidationException(ValidationException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", e.getMessage(), e));
    }

    @ExceptionHandler(NotFoundException.class)
    protected ResponseEntity<Object> handleNotFoundException(NotFoundException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.NOT_FOUND.value(), "Resource not found.", e.getMessage(), e));
    }

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.FORBIDDEN.value(), "Access denied", e.getMessage(), e));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Object> handleIOException(IOException e) {
        if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(e), "Broken pipe")) {
            this.jaxRsDpsLogger.warning("Client closed the connection while request still being processed");
            return null;
        } else {
            return this.getErrorResponse(
                    new AppException(HttpStatus.SERVICE_UNAVAILABLE.value(), "Unknown error", e.getMessage(), e));
        }
    }

    @ExceptionHandler(SchemaProcessingException.class)
    public ResponseEntity<Object> handleSchemaProcessingException(SchemaProcessingException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Error processing schema.", e.getMessage(), e));
    }

    /**
     * Unsupported request {@code Content-Type} (HTTP 415). Without this override,
     * {@link ResponseEntityExceptionHandler} returns RFC 7807 {@code application/problem+json}.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            @NonNull HttpMediaTypeNotSupportedException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Unsupported media type.",
                        e.getMessage(), e));
    }

    /**
     * Missing or unreadable request body. Without this override the default response is
     * {@code application/problem+json}.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Request is not valid.",
                        "Required request body is missing.", e));
    }

    /**
     * Query-parameter conversion failure (for example {@code force_clean=null}). Without this
     * override Spring returns RFC 7807 {@code application/problem+json}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String errorMessage = String.format("Invalid value '%s' for parameter '%s'", e.getValue(), e.getName());
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", errorMessage, e));
    }

    /**
     * Method-parameter validation ({@code @ValidKind} and similar). Default handling is
     * {@code application/problem+json}.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        List<String> errors = new ArrayList<>();
        e.getAllValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error -> {
                    String message = error.getDefaultMessage();
                    if (StringUtils.isNotBlank(message)) {
                        errors.add(message);
                    }
                }));
        String errorMessage = errors.isEmpty() ? "Validation error." : String.join("; ", errors);
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", errorMessage, e));
    }

    /**
     * Missing required {@code @RequestHeader} and other servlet binding failures
     * (for example {@code data-partition-id} on {@code PUT /partitions/provision}).
     * {@code MissingRequestHeaderException} is a {@link ServletRequestBindingException}.
     * Without this override the response is {@code application/problem+json}.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleServletRequestBindingException(
            @NonNull ServletRequestBindingException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Request is not valid.", e.getMessage(), e));
    }

    /**
     * Missing required {@code @RequestParam} (for example {@code kind} on {@code DELETE /index}).
     * Without this override the response is {@code application/problem+json}.
     */
    @Override
    @NonNull
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Request is not valid.", e.getMessage(), e));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "Validation error." : error.getDefaultMessage())
                .findFirst()
                .orElse("Validation error.");
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", errorMessage, ex));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleGeneralException(Exception e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Server error.",
                        "An unknown error has occurred.", e));
    }

    private ResponseEntity<Object> getErrorResponse(AppException e) {

        String exceptionMsg = e.getOriginalException() != null
                ? e.getOriginalException().getMessage()
                : e.getError().getMessage();

        if( e.getCause() instanceof Exception) {
            Exception original = (Exception) e.getCause();
            this.jaxRsDpsLogger.error(original.getMessage(), original);
        }

        if (e.getError().getCode() > 499) {
            this.jaxRsDpsLogger.error(exceptionMsg, e);
        } else {
            this.jaxRsDpsLogger.warning(exceptionMsg, e);
        }

        if (e.getError().getDebuggingInfo() != null) {
            this.jaxRsDpsLogger.warning(e.getError().getDebuggingInfo());
        }

        // Support for non standard HttpStatus Codes
        HttpStatus httpStatus = HttpStatus.resolve(e.getError().getCode());
        if (httpStatus == null) {
            return ResponseEntity.status(e.getError().getCode()).body(e);
        } else {
            return new ResponseEntity<>(e.getError(), httpStatus);
        }
    }
}
