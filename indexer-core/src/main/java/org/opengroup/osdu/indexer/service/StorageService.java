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

import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.RecordInfo;
import org.opengroup.osdu.core.common.model.indexer.RecordQueryResponse;
import org.opengroup.osdu.core.common.model.indexer.RecordReindexRequest;
import org.opengroup.osdu.core.common.model.indexer.Records;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.List;

public interface StorageService {

    Records getStorageRecords(List<String> ids, List<RecordInfo> recordChangedInfos) throws AppException, URISyntaxException;

    Records getStorageRecords(List<String> ids) throws URISyntaxException;

    RecordQueryResponse getRecordsByKind(RecordReindexRequest request) throws URISyntaxException;

    String getStorageSchema(String kind) throws URISyntaxException, UnsupportedEncodingException;

    List<String> getAllKinds() throws URISyntaxException;
}
