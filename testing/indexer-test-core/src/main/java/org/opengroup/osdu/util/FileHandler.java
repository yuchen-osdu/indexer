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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FileHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    private FileHandler(){}

    public static String readFile(String fileName) throws IOException {
        InputStream inputStream = getFileStream(fileName);
        if(inputStream == null) {
            throw new IOException();
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); 
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toString(StandardCharsets.UTF_8.toString());
    }

    public static <T> T readFile(String fileName, Class<T> targetClass) throws IOException {
        InputStream is = getFileStream(fileName);
        return mapper.readValue(is, targetClass);
    }

    private static InputStream getFileStream(String fileName) {
        return FileHandler.class.getResourceAsStream(String.format("/testData/%s", fileName));
    }
}
