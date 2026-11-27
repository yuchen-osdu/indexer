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

package org.opengroup.osdu.common;

import io.cucumber.java.Scenario;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.client.ElasticClient;
import org.opengroup.osdu.core.test.client.IndexerClient;
import org.opengroup.osdu.core.test.client.LegalTagsClient;
import org.opengroup.osdu.core.test.client.RetryConfiguration;
import org.opengroup.osdu.core.test.client.SchemaClient;
import org.opengroup.osdu.core.test.client.SearchClient;
import org.opengroup.osdu.core.test.client.StorageClient;
import org.opengroup.osdu.core.test.client.StringHttpClient;
import org.opengroup.osdu.core.test.client.TidyTestClientRegistry;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.legal.LegalTag;
import org.opengroup.osdu.core.test.client.model.legal.LegalTagProperties;
import org.opengroup.osdu.core.test.config.TestInitializer;
import org.opengroup.osdu.core.test.service.ServiceType;
import org.opengroup.osdu.models.TestIndex;
import org.opengroup.osdu.core.test.config.EnvLoader;
import org.opengroup.osdu.util.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for the indexer Cucumber acceptance tests.
 *
 * <p>Uses {@link TestInitializer#getSharedTestInitializer} to build the shared OpenID token
 * provider, service configuration, and HTTP client. All typed clients are {@code static final}
 * so they are created once per JVM and their tracked resources accumulate across scenarios until
 * {@link #tearDownTrackedClients()} is called.
 *
 * <p>The {@link ElasticClient} is created directly with connection parameters from {@link Config}
 * and is closed in {@link #tearDown()} once all test work is complete.
 */
@Slf4j
public abstract class TestsBase {

    /** Default user used for all indexer acceptance-test traffic. */
    protected static final UserType DEFAULT_USER = UserType.PRIVILEGED_USER;
    private static final String OTHER_RELEVANT_DATA_COUNTRY = "US";

    private static final List<UserType> USER_TYPES = List.of(UserType.PRIVILEGED_USER);

    /**
     * Services exercised by the indexer acceptance tests. {@code ENTITLEMENTS_V2} is required by
     * the os-core-test user-role verification performed during initialization.
     */
    private static final List<ServiceType> SERVICE_TYPES = List.of(
        ServiceType.INDEXER_V2,
        ServiceType.STORAGE_V2,
        ServiceType.SCHEMA_V1,
        ServiceType.SEARCH_V2,
        ServiceType.ENTITLEMENTS_V2,
        ServiceType.LEGAL_V1);

    private static final TestInitializer TEST_INITIALIZER = TestInitializer.getSharedTestInitializer(
        USER_TYPES, SERVICE_TYPES, RetryConfiguration.none());

    /** Shared HTTP client used to construct all typed clients. */
    protected static final StringHttpClient stringHttpClient = TEST_INITIALIZER.getStringHttpClient();

    /** Typed client for the Indexer v2 API (e.g. {@code DELETE /index}). */
    protected static final IndexerClient indexerClient =
        new IndexerClient(stringHttpClient, DEFAULT_USER);

    /** Typed client for the Storage v2 API (records CRUD). */
    protected static final StorageClient storageClient =
        new StorageClient(stringHttpClient, DEFAULT_USER);

    /** Typed client for the Search v2 API (query / cursor query). */
    protected static final SearchClient searchClient =
        new SearchClient(stringHttpClient, DEFAULT_USER);

    /** Typed client for the Entitlements v2 API (legal-tag management). */
    protected static final LegalTagsClient legalTagsClient =
        new LegalTagsClient(stringHttpClient, DEFAULT_USER);

    private static String suiteLegalTagName;

    /** Typed client for the Schema v1 API (schema creation). */
    protected static final SchemaClient schemaClient =
        new SchemaClient(stringHttpClient, DEFAULT_USER);

    /**
     * Shared Elasticsearch client created once per JVM with connection parameters from
     * {@link Config}. {@link ElasticClient} is {@link AutoCloseable}; call {@link #tearDown()}
     * (or its override in subclasses) to release the underlying connection pool.
     */
    protected static final ElasticClient elasticClient = new ElasticClient(
        Config.getElastic8UserName(),
        Config.getElastic8Password(),
        Config.getElastic8Host(),
        Config.getElastic8Port(),
        Config.isElastic8SslEnabled(),
        Config.isSecurityHttpsCertificateTrust());

    protected Scenario scenario;
    protected final Map<String, String> tenantMap = new HashMap<>();

    protected TestsBase() {
        initTenantMap();
    }

    private void initTenantMap() {
        tenantMap.put("tenant1", EnvLoader.get("DATA_PARTITION_ID", ""));
    }

    public static void setupSuiteLegalTag() {
        suiteLegalTagName = EnvLoader.get("DATA_PARTITION_ID", "osdu")
            + "-indexer-" + System.currentTimeMillis();
        LegalTagProperties properties = new LegalTagProperties(
            "A1234",
            "Default",
            List.of(OTHER_RELEVANT_DATA_COUNTRY),
            "Public",
            "EAR99",
            "No Personal Data",
            "2099-01-25",
            "Public Domain Data");
        LegalTag legalTag = new LegalTag(
            null,
            true,
            suiteLegalTagName,
            properties,
            "Legal tag for indexer acceptance tests");
        HttpResponse<LegalTag> response = legalTagsClient.create(legalTag);
        if (response.statusCode() != 201) {
            throw new AssertionError("Failed to create legal tag " + suiteLegalTagName
                + ", status=" + response.statusCode());
        }
        log.info("Created legal tag '{}' for acceptance test suite", suiteLegalTagName);
    }

    public static void tearDownSuiteLegalTag() {
        if (suiteLegalTagName == null) {
            return;
        }
        legalTagsClient.teardown();
        log.info("Deleted legal tag '{}' after acceptance test suite", suiteLegalTagName);
        suiteLegalTagName = null;
    }

    public static void tearDownTrackedResources() {
        TidyTestClientRegistry.teardownAll();
    }

    /**
     * Deletes resources tracked by typed clients and then invokes the legal tag client's own
     * teardown for the suite-level legal tag, matching the search acceptance-test lifecycle.
     */
    public static void tearDownTrackedClients() {
        tearDownTrackedResources();
        tearDownSuiteLegalTag();
    }

    protected TestIndex getTextIndex() {
        return new TestIndex(elasticClient, indexerClient, schemaClient, storageClient);
    }

    public void tearDown() {
        tearDownTrackedClients();
        elasticClient.close();
    }

    public String generateActualName(String rawName, String timeStamp) {
        for (Map.Entry<String, String> tenant : tenantMap.entrySet()) {
            rawName = rawName.replaceAll(tenant.getKey(), tenant.getValue());
        }
        return rawName.replaceAll("<timestamp>", timeStamp);
    }

    protected String generateActualId(String rawName, String timeStamp, String kind) {
        rawName = generateActualName(rawName, timeStamp);
        String kindSubType = kind.split(":")[2];
        return rawName.replaceAll("<kindSubType>", kindSubType);
    }

    public String generateActualNameWithoutTs(String rawName) {
        for (Map.Entry<String, String> tenant : tenantMap.entrySet()) {
            rawName = rawName.replaceAll(tenant.getKey(), tenant.getValue());
        }
        return rawName.replaceAll("<timestamp>", "");
    }

    public static Legal generateSuiteLegalTag() {
        if (suiteLegalTagName == null) {
            throw new IllegalStateException("Suite legal tag has not been created");
        }
        Legal legal = new Legal();
        legal.setLegaltags(Set.of(suiteLegalTagName));
        legal.setOtherRelevantDataCountries(Set.of(OTHER_RELEVANT_DATA_COUNTRY));
        return legal;
    }

    protected Legal generateLegalTag() {
        return generateSuiteLegalTag();
    }
}
