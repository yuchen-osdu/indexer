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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.codec.RedisCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.cache.JacksonCodec;
import org.opengroup.osdu.core.common.cache.JsonCodec;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.indexer.model.indexproperty.AugmenterConfiguration;

@ExtendWith(MockitoExtension.class)
class AugmenterRedisCacheTest {

    @Mock
    private RedisCache<String, String> configurationRedisCache;

    @Mock
    private RedisCache<String, Boolean> enabledRedisCache;

    @Test
    void configurationCacheShouldStoreConfigurationAsJson() throws Exception {
        AugmenterConfiguration configuration = readConfiguration();
        AugmenterConfigurationRedisCache cache = new AugmenterConfigurationRedisCache(configurationRedisCache);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);

        cache.put("key", configuration);
        verify(configurationRedisCache).put(eq("key"), stored.capture());
        when(configurationRedisCache.get("key")).thenReturn(stored.getValue());

        AugmenterConfiguration cached = cache.get("key");
        assertEquals(configuration, cached);
        assertTrue(cached.isValid());
        assertEquals(configuration.getUniqueRelatedObjectKinds(), cached.getUniqueRelatedObjectKinds());
    }

    @Test
    void configurationCacheShouldReturnNullForMissingKey() {
        AugmenterConfigurationRedisCache cache = new AugmenterConfigurationRedisCache(configurationRedisCache);
        assertNull(cache.get("missing"));
    }

    @Test
    void emptyConfigurationShouldStayEmptyInCache() {
        // getConfiguration() caches an empty configuration for kinds without one
        // and treats a configuration without Code as empty
        AugmenterConfigurationRedisCache cache = new AugmenterConfigurationRedisCache(configurationRedisCache);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);

        cache.put("key", new AugmenterConfiguration());
        verify(configurationRedisCache).put(eq("key"), stored.capture());
        when(configurationRedisCache.get("key")).thenReturn(stored.getValue());

        AugmenterConfiguration cached = cache.get("key");
        assertNotNull(cached);
        assertNull(cached.getCode());
    }

    @Test
    void storedJsonShouldSurviveGsonAndJacksonCodecs() throws IOException {
        // The cached value is a plain string, so it does not depend on cache.codec
        AugmenterConfigurationRedisCache cache = new AugmenterConfigurationRedisCache(configurationRedisCache);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        cache.put("key", readConfiguration());
        verify(configurationRedisCache).put(eq("key"), stored.capture());

        assertEquals(stored.getValue(), roundTrip(new JsonCodec<>(String.class, String.class), stored.getValue()));
        assertEquals(stored.getValue(), roundTrip(new JacksonCodec<>(String.class, String.class), stored.getValue()));
    }

    @Test
    void configurationCacheShouldDelegateDeleteClearAndClose() throws Exception {
        AugmenterConfigurationRedisCache cache = new AugmenterConfigurationRedisCache(configurationRedisCache);

        cache.delete("key");
        cache.clearAll();
        cache.close();

        verify(configurationRedisCache).delete("key");
        verify(configurationRedisCache).clearAll();
        verify(configurationRedisCache).close();
    }

    @Test
    void enabledCacheShouldDelegateToRedis() throws Exception {
        AugmenterConfigurationEnabledRedisCache cache = new AugmenterConfigurationEnabledRedisCache(enabledRedisCache);
        when(enabledRedisCache.get("key")).thenReturn(true);

        cache.put("key", true);
        assertTrue(cache.get("key"));
        assertNull(cache.get("missing"));
        cache.delete("key");
        cache.clearAll();
        cache.close();

        verify(enabledRedisCache).put("key", true);
        verify(enabledRedisCache).delete("key");
        verify(enabledRedisCache).clearAll();
        verify(enabledRedisCache).close();
    }

    private String roundTrip(RedisCodec<String, String> codec, String value) {
        ByteBuffer encoded = codec.encodeValue(value);
        return codec.decodeValue(encoded);
    }

    private AugmenterConfiguration readConfiguration() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/indexproperty/well_configuration_record.json")) {
            return new ObjectMapper().readValue(stream, AugmenterConfiguration.class);
        }
    }
}
