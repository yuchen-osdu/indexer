/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.util;

import java.util.Map;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;

public interface IHeadersInfo {

	DpsHeaders getHeaders();

	String getUser();

	String getPartitionId();

	String getPrimaryPartitionId();

	Map<String, String> getHeadersMap();

	DpsHeaders getCoreServiceHeaders(Map<String, String> input);

}
