/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.util;

import static org.opengroup.osdu.core.common.model.http.DpsHeaders.AUTHORIZATION;

import java.util.Map;

import jakarta.inject.Inject;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.DeploymentEnvironment;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.IRequestInfo;
import org.opengroup.osdu.core.common.search.Preconditions;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;

@Primary

@Component
//@RequestScope
public class RequestInfoImpl implements IRequestInfo {

    @Inject
    private DpsHeaders headersMap;
    
    @Inject
    private JaxRsDpsLog logger;

    @Inject
    private IServiceAccountJwtClient serviceAccountJwtClient;

    @Inject
    private TenantInfo tenantInfo;
    
    @Value("${DEPLOYMENT_ENVIRONMENT}")
    private String DEPLOYMENT_ENVIRONMENT;

    private static final String INDEXER_API_KEY_HEADER="x-api-key";
    
    @Value("${INDEXER_API_KEY}")
    private String tokenFromProperty;

    @Override
    public DpsHeaders getHeaders() {
        if (headersMap == null) {
            logger.warning("Headers Map DpsHeaders is null");
            // throw to prevent null reference exception below
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Invalid Headers", "Headers Map DpsHeaders is null");
        }
		DpsHeaders headers = this.getCoreServiceHeaders(headersMap.getHeaders());

		if (headers.getHeaders().containsKey(INDEXER_API_KEY_HEADER)) {
			String apiToken = headers.getHeaders().get(INDEXER_API_KEY_HEADER);
			if (!apiToken.equals(tokenFromProperty)) {
				logger.error("Indexer API Token in header is mismatched");
				throw new AppException(HttpStatus.SC_UNAUTHORIZED, "Indexer API Token in header mismatched.", "Indexer API Token in header mismatched.");
			}
		} else {
			throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Missing Header", "The headers "+ INDEXER_API_KEY_HEADER + "  is missing!");
		}
		return headers;
	}

    @Override
    public String getPartitionId() {
        return getHeaders().getPartitionIdWithFallbackToAccountId();
    }

    @Override
    public Map<String, String> getHeadersMap() {
        return getHeaders().getHeaders();
    }

    @Override
    public Map<String, String> getHeadersMapWithDwdAuthZ() {
        return getHeadersWithDwdAuthZ().getHeaders();
    }

    @Override
    public DpsHeaders getHeadersWithDwdAuthZ() {
        this.headersMap.put(AUTHORIZATION, this.checkOrGetAuthorizationHeader());
        return getHeaders();
    }

    @Override
    public boolean isCronRequest() { return false;}

    @Override
    public boolean isTaskQueueRequest() {
        //if (!this.dpsHeaders.getHeaders().containsKey(INDEXER_API_KEY_HEADER)) return false;
    	

//        String queueId = this.headersInfo.getHeadersMap().get(AppEngineHeaders.TASK_QUEUE_NAME);
//        return queueId.endsWith(Constants.INDEXER_QUEUE_IDENTIFIER);
        return false;
    }

    public String checkOrGetAuthorizationHeader() {
        if (DeploymentEnvironment.valueOf(DEPLOYMENT_ENVIRONMENT) == DeploymentEnvironment.LOCAL) {
            String authHeader = this.headersMap.getAuthorization();
            if (Strings.isNullOrEmpty(authHeader)) {
            	logger.error(HttpStatus.SC_UNAUTHORIZED + " : Invalid authorization header");
                throw new AppException(HttpStatus.SC_UNAUTHORIZED, "Invalid authorization header", "Authorization token cannot be empty");
            }
            return "Bearer " + authHeader;
        } else {
            return this.serviceAccountJwtClient.getIdToken(tenantInfo.getName());
        }
    }
    
    private DpsHeaders getCoreServiceHeaders(Map<String, String> input) {
        Preconditions.checkNotNull(input, "input headers cannot be null");
        DpsHeaders output = DpsHeaders.createFromMap(input);
        return output;
    }
}
