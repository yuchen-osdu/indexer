/*
 * Copyright 2021 Google LLC
 * Copyright 2021 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.info.ConnectedOuterServicesBuilder;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.IElasticSettingService;
import org.opengroup.osdu.core.common.model.info.ConnectedOuterService;
import org.opengroup.osdu.core.common.model.search.ClusterSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@ConditionalOnMissingBean(type = "ConnectedOuterServicesBuilder")
@Slf4j
@RequestScope
public class CloudConnectedOuterServicesBuilder implements ConnectedOuterServicesBuilder {

  private static final String NAME_PREFIX = "ElasticSearch-";
  private static final String REDIS_PREFIX = "Redis-";
  private static final String NOT_AVAILABLE = "N/A";

  private final List<RedisCache> redisCaches;
  private final ElasticClientHandler elasticClient;
  private final IElasticSettingService elasticSettingService;

  public CloudConnectedOuterServicesBuilder(
      List<RedisCache> redisCaches,
      ElasticClientHandler elasticClient,
      IElasticSettingService elasticSettingService) {
    this.redisCaches = redisCaches;
    this.elasticClient = elasticClient;
    this.elasticSettingService = elasticSettingService;
  }

  @Override
  public List<ConnectedOuterService> buildConnectedOuterServices() {
    return Stream.concat(redisCaches.stream().map(this::fetchRedisInfo),
        fetchElasticInfos().stream())
        .collect(Collectors.toList());
  }

  private ConnectedOuterService fetchRedisInfo(RedisCache cache) {
    String redisVersion = StringUtils.substringBetween(cache.info(), ":", "\r");
    return ConnectedOuterService.builder()
        .name(REDIS_PREFIX + StringUtils.substringAfterLast(cache.getClass().getName(), "."))
        .version(redisVersion)
        .build();
  }

  private List<ConnectedOuterService> fetchElasticInfos() {
    try {
      return elasticSettingService.getAllClustersSettings()
          .entrySet().stream()
          .map(entry -> fetchElasticInfo(entry.getKey(), entry.getValue()))
          .collect(Collectors.toList());
    } catch (AppException e) {
      log.error("Can't fetch cluster settings", e.getOriginalException());
      return Collections.singletonList(ConnectedOuterService.builder()
          .name(NAME_PREFIX + NOT_AVAILABLE)
          .version(NOT_AVAILABLE)
          .build());
    }
  }

  private ConnectedOuterService fetchElasticInfo(String partitionId, ClusterSettings settings) {
    try {
      ElasticsearchClient client = elasticClient.createRestClient(settings);
      return ConnectedOuterService.builder()
          .name(NAME_PREFIX + partitionId)
          .version(client.info().version().number())
          .build();
    } catch (AppException e) {
      log.error("Can't create elastic client", e.getOriginalException());
      return ConnectedOuterService.builder()
          .name(NAME_PREFIX + partitionId)
          .version(NOT_AVAILABLE)
          .build();
    } catch (IOException e) {
      log.error("Can't fetch elastic info.", e);
      return ConnectedOuterService.builder()
          .name(NAME_PREFIX + partitionId)
          .version(NOT_AVAILABLE)
          .build();
    }
  }
}
