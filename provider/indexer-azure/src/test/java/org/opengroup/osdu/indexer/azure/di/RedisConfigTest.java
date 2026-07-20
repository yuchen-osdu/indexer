package org.opengroup.osdu.indexer.azure.di;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opengroup.osdu.azure.di.RedisAzureConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RedisConfigTest {

    @ParameterizedTest(name = "{0} should return {1}")
    @CsvSource({
        "indexRedisTtl, 4000",
        "jwtTtl, 3000",
        "schemaTtl, 2000",
        "recordsTtl, 500",
        "recordChangeInfoTtl, 200"
    })
    public void shouldReturnCorrectTtlValue(String fieldName, int testValue) {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, fieldName, testValue);

        int result = switch (fieldName) {
            case "indexRedisTtl" -> config.getIndexRedisTtl();
            case "jwtTtl" -> config.getJwtTtl();
            case "schemaTtl" -> config.getSchemaTtl();
            case "recordsTtl" -> config.getRecordsTtl();
            case "recordChangeInfoTtl" -> config.getRecordChangeInfoTtl();
            default -> throw new IllegalArgumentException("Unknown field: " + fieldName);
        };

        assertEquals(testValue, result);
    }

    @ParameterizedTest(name = "hostname={0}, principalId={1}")
    @CsvSource(value = {
        "primary.redis.example.com, test-principal-id",
        "primary.redis.example.com, NULL",
        "NULL, test-principal-id",
        "NULL, NULL"
    }, nullValues = "NULL")
    public void shouldCreateConfiguration_withVariousHostnameAndPrincipalIdCombinations(String hostname, String principalId) {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "database", 1);
        ReflectionTestUtils.setField(config, "port", 6380);
        ReflectionTestUtils.setField(config, "connectionTimeout", 15);
        ReflectionTestUtils.setField(config, "commandTimeout", 5);
        ReflectionTestUtils.setField(config, "principalId", principalId);
        ReflectionTestUtils.setField(config, "hostname", hostname);

        RedisAzureConfiguration result = config.createConfiguration(3600);

        assertNotNull(result);
        // Verify the hostname and principalId were set correctly
        Object actualHostname = ReflectionTestUtils.getField(result, "hostname");
        Object actualPrincipalId = ReflectionTestUtils.getField(result, "principalId");
        assertEquals(hostname, actualHostname, 
            "Hostname must be set correctly in the configuration");
        assertEquals(principalId, actualPrincipalId,
            "PrincipalId must be set correctly in the configuration");
    }
}
