// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.indexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.search.IndexInfo;

public interface IndicesService {

    boolean createIndex(ElasticsearchClient client, String index, IndexSettings settings, Map<String, Object> mapping) throws ElasticsearchException, IOException;

    boolean isIndexExist(ElasticsearchClient client, String index) throws IOException;

    boolean deleteIndex(ElasticsearchClient client, String index) throws ElasticsearchException, IOException, AppException;

    boolean deleteIndex(String index) throws ElasticsearchException, IOException, AppException;

    List<IndexInfo> getIndexInfo(ElasticsearchClient client, String indexPattern) throws IOException;

    boolean isIndexReady(ElasticsearchClient client, String index) throws IOException;
}
