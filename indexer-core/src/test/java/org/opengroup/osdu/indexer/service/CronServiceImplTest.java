/*
 *  Copyright 2020-2024 Google LLC
 *  Copyright 2020-2024 EPAM Systems, Inc
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

package org.opengroup.osdu.indexer.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.IndexInfo;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.indexer.config.IndexerConfigurationProperties;
import org.opengroup.osdu.indexer.util.RequestScopedElasticsearchClient;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class CronServiceImplTest {

    @Mock
    private IndexerConfigurationProperties configurationProperties;

    @Mock
    private ElasticsearchClient restHighLevelClient;
    @Mock
    private IndicesService indicesService;
    @Mock
    private RequestScopedElasticsearchClient requestScopedClient;
    @Mock
    private IRequestInfo requestInfo;
    @Mock
    private JaxRsDpsLog log;
    @InjectMocks
    private CronServiceImpl sut;

    @InjectMocks
    private DpsHeaders dpsHeaders;

    @Before
    public void setup() {

        when(this.requestInfo.getHeaders()).thenReturn(dpsHeaders);
        when(requestScopedClient.getClient()).thenReturn(restHighLevelClient);
        when(configurationProperties.getCronIndexCleanupThresholdDays()).thenReturn(3);
        when(configurationProperties.getCronEmptyIndexCleanupThresholdDays()).thenReturn(7);
    }

    @Test
    public void run_cleanup_when_cron_job_runs_with_correct_pattern() throws Exception {
        final String indexPattern = "tenant1-index-*";

        IndexInfo info = IndexInfo.builder().name("tenant1-index-1.0.0").documentCount("10").creationDate(Long.toString(Instant.now().minus(4, ChronoUnit.DAYS).toEpochMilli())).build();

        when(this.requestInfo.getPartitionId()).thenReturn("tenant1");
        when(this.indicesService.getIndexInfo(this.restHighLevelClient, indexPattern)).thenReturn(Lists.newArrayList(info));

        this.sut.cleanupIndices(indexPattern);

        verify(this.indicesService, times(1)).deleteIndex(restHighLevelClient, "tenant1-index-1.0.0");
        verify(this.indicesService, times(1)).getIndexInfo(restHighLevelClient, indexPattern);
    }

    @Test(expected = IOException.class)
    public void run_cleanup_when_cron_job_runs_with_wrong_pattern() throws Exception {
        IOException exception = new IOException("blah");
        when(this.indicesService.getIndexInfo(this.restHighLevelClient, "tenant1-test-*")).thenThrow(exception);

        this.sut.cleanupIndices("tenant1-test-*");

        verify(this.indicesService, times(0)).deleteIndex(any(), any());
    }

    @Test
    public void run_cleanup_when_backend_does_not_have_empty_stale_indices() throws Exception {
        IndexInfo info = IndexInfo.builder().name("tenant1-index-1.0.0").documentCount("10").creationDate(Long.toString(Instant.now().minus(8, ChronoUnit.DAYS).toEpochMilli())).build();

        when(this.requestInfo.getPartitionId()).thenReturn("tenant1");
        when(this.indicesService.getIndexInfo(this.restHighLevelClient, null)).thenReturn(Lists.newArrayList(info));

        this.sut.cleanupEmptyStaleIndices();

        verify(this.indicesService, times(0)).deleteIndex(restHighLevelClient, null);
        verify(this.indicesService, times(1)).getIndexInfo(restHighLevelClient, null);
    }

    @Test
    public void run_cleanup_when_backend_have_empty_stale_indices() throws Exception {
        IndexInfo info = IndexInfo.builder().name("tenant1-index-1.0.0").documentCount("0").creationDate(Long.toString(Instant.now().minus(8, ChronoUnit.DAYS).toEpochMilli())).build();

        when(this.requestInfo.getPartitionId()).thenReturn("tenant1");
        when(this.indicesService.getIndexInfo(this.restHighLevelClient, null)).thenReturn(Lists.newArrayList(info));

        this.sut.cleanupEmptyStaleIndices();

        verify(this.indicesService, times(1)).deleteIndex(restHighLevelClient, "tenant1-index-1.0.0");
        verify(this.indicesService, times(1)).getIndexInfo(restHighLevelClient, null);
    }

    @Test(expected = IOException.class)
    public void run_cleanup_when_backend_throws_exception() throws Exception {
        IOException exception = new IOException("blah");
        when(this.indicesService.getIndexInfo(this.restHighLevelClient, null)).thenThrow(exception);

        this.sut.cleanupEmptyStaleIndices();

        verify(this.indicesService, times(0)).deleteIndex(any(), any());
    }
}
