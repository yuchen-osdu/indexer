/*
  Copyright 2026 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.indexer.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import java.util.function.Function;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.RedisCache;

/**
 * Stores values as JSON strings, so the serializer is chosen per value type instead of by cache.codec.
 */
public abstract class JsonRedisCache<V> implements ICache<String, V>, AutoCloseable {

    private static final Gson GSON = new Gson();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RedisCache<String, String> cache;
    private final Function<V, String> serializer;
    private final Function<String, V> deserializer;

    protected JsonRedisCache(RedisCache<String, String> cache, Function<V, String> serializer,
                             Function<String, V> deserializer) {
        this.cache = cache;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    protected static <T> Function<T, String> gsonSerializer() {
        return GSON::toJson;
    }

    protected static <T> Function<String, T> gsonDeserializer(Class<T> type) {
        return json -> GSON.fromJson(json, type);
    }

    // Unlike Gson, Jackson keeps integers as Integer/Long in Map<String, Object>, which change detection compares with equals
    protected static <T> Function<T, String> jacksonSerializer() {
        return value -> {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize cache value", e);
            }
        };
    }

    protected static <T> Function<String, T> jacksonDeserializer(Class<T> type) {
        return json -> {
            try {
                return OBJECT_MAPPER.readValue(json, type);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize cache value", e);
            }
        };
    }

    @Override
    public void close() throws Exception {
        this.cache.close();
    }

    @Override
    public void put(String s, V o) {
        this.cache.put(s, serializer.apply(o));
    }

    @Override
    public V get(String s) {
        String json = this.cache.get(s);
        return json == null ? null : deserializer.apply(json);
    }

    @Override
    public void delete(String s) {
        this.cache.delete(s);
    }

    @Override
    public void clearAll() {
        this.cache.clearAll();
    }
}
