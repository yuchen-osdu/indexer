/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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
package org.opengroup.osdu.indexer.indexing.processing;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.opengroup.osdu.oqm.core.model.OqmMessage;

public class ReadFromFileUtil {

  private final static Gson gson = new Gson();

  public static OqmMessage readEventFromFile(String filename) {
    InputStream resourceAsStream = ReadFromFileUtil.class.getResourceAsStream(filename);
    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resourceAsStream));
    JsonReader reader = new JsonReader(bufferedReader);
    return gson.fromJson(reader, OqmMessage.class);
  }

}
