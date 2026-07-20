/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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
package org.opengroup.osdu.indexer.web.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AppExceptionHandlerTest {

    @InjectMocks
    private AppExceptionHandler appExceptionHandler;

    @BeforeEach
    void setUp() {
        // Setup is done in individual tests as AppError is created per test
    }

    @Test
    void testHandleAppExceptions_WithOriginalException_ServerError() {
        // Arrange
        Exception originalException = new RuntimeException("Original error message");
        AppException appException = new AppException(500, "Internal Server Error", "Test reason", originalException);

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(appException.getError(), response.getBody());
    }

    @Test
    void testHandleAppExceptions_WithOriginalException_ClientError() {
        // Arrange
        Exception originalException = new IllegalArgumentException("Invalid input");
        AppException appException = new AppException(400, "Bad Request", "Test reason", originalException);

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(appException.getError(), response.getBody());
    }

    @Test
    void testHandleAppExceptions_WithoutOriginalException() {
        // Arrange
        AppException appException = new AppException(404, "Not Found", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(appException.getError(), response.getBody());
    }

    @Test
    void testHandleAppExceptions_WithCode401() {
        // Arrange
        AppException appException = new AppException(401, "Unauthorized", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testHandleAppExceptions_WithCode403() {
        // Arrange
        AppException appException = new AppException(403, "Forbidden", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testResolveNotSupportedStatus_InformationalRange() {
        // Arrange
        AppException appException = new AppException(150, "Custom Informational", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CONTINUE, response.getStatusCode());
    }

    @Test
    void testResolveNotSupportedStatus_SuccessRange() {
        // Arrange
        AppException appException = new AppException(250, "Custom Success", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void testResolveNotSupportedStatus_RedirectionRange() {
        // Arrange
        AppException appException = new AppException(350, "Custom Redirection", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.MULTIPLE_CHOICES, response.getStatusCode());
    }

    @Test
    void testResolveNotSupportedStatus_ClientErrorRange() {
        // Arrange
        AppException appException = new AppException(450, "Custom Client Error", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testResolveNotSupportedStatus_OutOfRangeCode() {
        // Arrange
        AppException appException = new AppException(600, "Out of range code", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testHandleAppExceptions_StandardHttpStatusCodes() {
        // Test some standard HTTP status codes that should be resolved normally
        int[] standardCodes = {200, 201, 204, 301, 302, 400, 401, 403, 404, 500, 502, 503};

        for (int code : standardCodes) {
            AppException appException = new AppException(code, "Test message", "Test reason");

            ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

            assertNotNull(response);
            assertEquals(code, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(appException.getError(), response.getBody());
        }
    }

    @Test
    void testHandleAppExceptions_BoundaryCode499() {
        // Arrange - 499 is not a standard HTTP code, so it falls back to BAD_REQUEST (400)
        // This tests the boundary between client and server errors for logging (should log as warning)
        AppException appException = new AppException(499, "Client Closed Request", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        // 499 is not recognized by HttpStatus.resolve(), so resolveNotSupportedStatus returns BAD_REQUEST
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void testHandleAppExceptions_BoundaryCode500() {
        // Arrange - boundary between client and server errors for logging
        AppException appException = new AppException(500, "Internal Server Error", "Test reason");

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testHandleAppExceptions_WithNullOriginalExceptionMessage() {
        // Arrange
        Exception originalException = new RuntimeException((String) null);
        AppException appException = new AppException(404, "Not Found", "Test reason", originalException);

        // Act
        ResponseEntity<Object> response = appExceptionHandler.handleAppExceptions(appException);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(appException.getError(), response.getBody());
    }
}
