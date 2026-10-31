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

package org.opengroup.osdu.indexer.error;

import javassist.NotFoundException;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.RequestStatus;
import org.opengroup.osdu.indexer.schema.converter.exeption.SchemaProcessingException;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ValidationException;

import java.io.IOException;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class GlobalExceptionMapperCoreTest {

    @Mock
    private JaxRsDpsLog log;
    @InjectMocks
    private GlobalExceptionMapperCore sut;

    @Test
    public void should_useValuesInAppExceptionInResponse_When_AppExceptionIsHandledByGlobalExceptionMapper() {
        AppException exception = new AppException(409, "any reason", "any message");

        ResponseEntity<Object> response = sut.handleAppException(exception);
        assertEquals(409, response.getStatusCodeValue());
        assertEquals(exception.getError(), response.getBody());
    }

    @Test
    public void should_use404ValueInResponse_When_NotFoundExceptionIsHandledByGlobalExceptionMapper() {
        NotFoundException exception = new NotFoundException("any message");

        ResponseEntity<Object> response = sut.handleNotFoundException(exception);
        assertEquals(404, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("any message"));
    }

    @Test
    public void should_useGenericValuesInResponse_When_ExceptionIsHandledByGlobalExceptionMapper() {
        Exception exception = new Exception("any message");

        ResponseEntity<Object> response = sut.handleGeneralException(exception);
        assertEquals(500, response.getStatusCodeValue());
        assertEquals("AppError(code=500, reason=Server error., message=An unknown error has occurred., errors=null, debuggingInfo=null, originalException=java.lang.Exception: any message)", response.getBody().toString());
    }

    @Test
    public void should_useBadRequestInResponse_When_handleValidationExceptionIsHandledByGlobalExceptionMapper() {
        ValidationException exception = new ValidationException();

        ResponseEntity<Object> response = sut.handleValidationException(exception);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
    }

    @Test
    public void should_useBadRequestInResponse_When_handleAccessDeniedExceptionIsHandledByGlobalExceptionMapper() {
        AccessDeniedException exception = new AccessDeniedException("Access is denied.");

        ResponseEntity<Object> response = sut.handleAccessDeniedException(exception);
        assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatusCodeValue());
    }

    @Test
    public void should_useCustomErrorCodeInResponse_When_AppExceptionIsHandledByGlobalExceptionMapper() {
        AppException exception = new AppException(RequestStatus.INVALID_RECORD, "Invalid request", "Successful Storage service response with wrong json");

        ResponseEntity<Object> response = sut.handleAppException(exception);
        assertEquals(RequestStatus.INVALID_RECORD, response.getStatusCodeValue());
    }

    @Test
    public void should_returnNullResponse_when_BrokenPipeIOExceptionIsCaptured() {
        IOException ioException = new IOException("Broken pipe");

        ResponseEntity response = this.sut.handleIOException(ioException);
        assertNull(response);
    }

    @Test
    public void should_returnServiceUnavailable_when_IOExceptionIsCaptured() {
        IOException ioException = new IOException("Not broken yet");

        ResponseEntity response = this.sut.handleIOException(ioException);
        assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, response.getStatusCodeValue());
    }

    @Test
    public void should_returnAppErrorJson_when_mediaTypeIsUnsupported() {
        HttpMediaTypeNotSupportedException exception = new HttpMediaTypeNotSupportedException("Content-Type 'null' is not supported.");

        ResponseEntity<Object> response = sut.handleHttpMediaTypeNotSupported(exception, new HttpHeaders(), org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, null);
        assertEquals(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("Unsupported media type."));
    }

    @Test
    public void should_returnBadRequest_when_requestBodyIsMissing() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("Failed to read request");

        ResponseEntity<Object> response = sut.handleHttpMessageNotReadable(exception, new HttpHeaders(), org.springframework.http.HttpStatus.BAD_REQUEST, null);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("Request is not valid."));
    }

    @Test
    public void should_returnBadRequest_when_queryParameterCannotBeConverted() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException("null", Boolean.class, "force_clean", null, new IllegalArgumentException("bad"));

        ResponseEntity<Object> response = sut.handleMethodArgumentTypeMismatch(exception);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("force_clean"));
    }

    @Test
    public void should_returnBadRequest_when_requiredHeaderIsMissing() {
        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.getNestedParameterType()).thenReturn((Class) String.class);
        MissingRequestHeaderException exception = new MissingRequestHeaderException("data-partition-id", parameter);

        ResponseEntity<Object> response = sut.handleServletRequestBindingException(
                exception, new HttpHeaders(), org.springframework.http.HttpStatus.BAD_REQUEST, null);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("data-partition-id"));
    }

    @Test
    public void should_returnBadRequest_when_requiredQueryParameterIsMissing() {
        MissingServletRequestParameterException exception = new MissingServletRequestParameterException("kind", "String");

        ResponseEntity<Object> response = sut.handleMissingServletRequestParameter(exception, new HttpHeaders(), org.springframework.http.HttpStatus.BAD_REQUEST, null);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("kind"));
    }

    @Test
    public void should_returnAppException_when_SchemaProcessingExceptionIsThrown() {
        SchemaProcessingException schemaProcessingException = new SchemaProcessingException("error processing schema");
        
        ResponseEntity response = this.sut.handleSchemaProcessingException(schemaProcessingException);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
    }
}
