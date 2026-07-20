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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple HTTP response wrapper that provides a compatible interface
 * for test code, replacing the Jersey ClientResponse.
 *
 * This class wraps Apache HttpClient 5 responses and provides the same
 * methods that were used from Jersey's ClientResponse.
 */
public class HttpResponse {

    private final int statusCode;
    private final String body;
    private final Map<String, List<String>> headers;
    private final String contentType;

    public HttpResponse(int statusCode, String body, Map<String, List<String>> headers, String contentType) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers != null ? headers : new HashMap<>();
        this.contentType = contentType != null ? contentType : "application/json";
    }

    /**
     * Returns the HTTP status code.
     */
    public int getStatus() {
        return statusCode;
    }

    /**
     * Returns the response body as the specified type.
     * Currently only supports String.class.
     */
    @SuppressWarnings("unchecked")
    public <T> T getEntity(Class<T> type) {
        if (type == String.class) {
            if (body == null) {
                return null;
            }
            return (T) body;
        }
        throw new UnsupportedOperationException("Only String.class is supported for getEntity()");
    }

    /**
     * Returns the response headers as a MultivaluedMap-like structure.
     * Keys are header names, values are lists of header values.
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Returns the content type of the response as a string.
     */
    public String getType() {
        return contentType;
    }

    /**
     * Returns status information including the response family.
     */
    public StatusInfo getStatusInfo() {
        return new StatusInfo(statusCode);
    }

    /**
     * Inner class to provide status family information.
     */
    public static class StatusInfo {
        private final int statusCode;

        public StatusInfo(int statusCode) {
            this.statusCode = statusCode;
        }

        /**
         * Returns the response status family.
         */
        public StatusFamily getFamily() {
            if (statusCode < 100 || statusCode >= 600) {
                throw new IllegalArgumentException("Invalid HTTP status code: " + statusCode);
            } else if (statusCode < 200) {
                return StatusFamily.INFORMATIONAL;
            } else if (statusCode < 300) {
                return StatusFamily.SUCCESSFUL;
            } else if (statusCode < 400) {
                return StatusFamily.REDIRECTION;
            } else if (statusCode < 500) {
                return StatusFamily.CLIENT_ERROR;
            } else {
                return StatusFamily.SERVER_ERROR;
            }
        }
    }

    /**
     * Enum representing HTTP response status families.
     */
    public enum StatusFamily {
        INFORMATIONAL,
        SUCCESSFUL,
        REDIRECTION,
        CLIENT_ERROR,
        SERVER_ERROR,
        OTHER
    }

    /**
     * Helper method to create a headers map from key-value pairs.
     */
    public static Map<String, List<String>> createHeaders() {
        return new HashMap<>();
    }

    /**
     * Helper method to add a header to the headers map.
     */
    public static void addHeader(Map<String, List<String>> headers, String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    @Override
    public String toString() {
        return "HttpResponse{" +
                "statusCode=" + statusCode +
                ", contentType='" + contentType + '\'' +
                ", bodyLength=" + (body != null ? body.length() : 0) +
                '}';
    }
}
