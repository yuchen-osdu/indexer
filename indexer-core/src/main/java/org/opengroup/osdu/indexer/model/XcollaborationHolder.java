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

package org.opengroup.osdu.indexer.model;

import static org.opengroup.osdu.indexer.config.IndexerConfigurationProperties.COLLABORATIONS_FEATURE_NAME;

import java.util.Optional;
import lombok.Data;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.search.RecordMetaAttribute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
  This bean is used to pass new x-collaboration header value in logic that is behind the '/index-worker' endpoint.

  Record has id in osdu format. This id is used in RecordStatus.
  Example: osdu:master-data--CollaborationProject:xxx
  The same id will then be used to index records with _id in Elasticsearch.
  If we have an x-collaboration header in request, we need to concatenate Record's id with
  x-collaboration value because we want to index a Record with different id, not its own.
  In constructed id will be coded a name from Work In Progress namespace (x-collaboration header value).
  Example: osdu:master-data--CollaborationProject:xxx:id=a99cef48-2ed6-4beb-8a43-002373431f21,application=pws
  We need to have two docs in Elasticsearch having different _id because they are in different namespaces,
  byt pointing to the same Record's id.

  Also, we need to add x-collaboration field in Elasticsearch's metadata.
  It will enable us to search for Records in any namespace in Elasticsearch by using this query

  {
    "query": {
     "exists": {
       "field": "x-collaboration"
      }
    }
  }

 or

 {
   "query": {
     "bool": {
       "must_not": {
         "exists": {
           "field": "x-collaboration"
          }
       }
     }
   }
 }

 This allows multiple instances of the same Record ID to exist in Search, 1 per collaboration.

 Messages, that will be pushed to queue
 {
   "data":"[
              {
                \"id\":\"osdu:master-data--CollaborationProject:xxxxxxxxx\",
                \"kind\":\"osdu:wks:master-data--CollaborationProject:1.0.0\",
                \"op\":\"create\"
              }
            ]",
   "attributes":{
              "correlation-id":"4c3674e9-7337-43cd-86f4-9ca486501abb",
              "x-collaboration":"id-as-uuid,application-pws",
              "data-partition-id":"osdu"
   }
 }

 */
@Data
@Component
@RequestScope
public class XcollaborationHolder {

  public static final String X_COLLABORATION = RecordMetaAttribute.COLLABORATION_ID.getValue();
  private Optional<CollaborationContext> collaborationContext = Optional.<CollaborationContext>empty();

    @Autowired
    private IFeatureFlag featureFlagChecker;

    @Autowired
    CollaborationContextFactory collaborationContextFactory;

    // need to have header's value as is to be able to execute a http's requests at least to a Storage service
    private String xCollaborationHeader;

    public void setxCollaborationHeader(String xCollaborationHeader) {
        this.xCollaborationHeader = xCollaborationHeader;
        collaborationContext = collaborationContextFactory.create(xCollaborationHeader);
    }

    /**
     * Example of x-collab request is
     *   "attributes": {
     *         "account-id": "osdu",
     *         "x-collaboration": "id={{collab-id}},application={{app-id}}"
     *     }
     * */
    public boolean isXcollaborationHeaderExists() {
        return collaborationContext.isPresent();
    }

    /**
     * Feature could be disabled on app level.
     */
    public boolean isFeatureEnabled() {
        return featureFlagChecker.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME);
    }

    /**
     * With respect to both conditions: if feature is enabled and x-collaboration header exists.
     */
    public boolean isFeatureEnabledAndHeaderExists() {
        return isFeatureEnabled() && isXcollaborationHeaderExists();
    }

    /**
     * Remove from string
     * 'osdu:master-data--CollaborationProject:xxx:a99cef48-2ed6-4beb-8a43-002373431f21' any header
     * value if it exists.
     */
    public String removeXcollaborationValue(String input) {
        if (input == null) {
            return input;
        }

        String result = input.replaceAll(collaborationContext.orElseThrow().getId(), "");
        if (result.endsWith(
            ":")) { // id should not be ended with semicolon
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
