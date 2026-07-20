/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.util;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
@Primary
public class DpsHeadersIBMQueue extends DpsHeaders {

    @Value("${indexer.queue.key}")
    private String queueKey;

    //TODO this should be moved to Azure client-lib
    public static final String INDEXER_QUEUE_KEY = "x-functions-key";
    
    @Inject
    public DpsHeadersIBMQueue(HttpServletRequest request) {

        Map<String, String> headers = Collections
                .list(request.getHeaderNames())
                .stream()
                .collect(Collectors.toMap(h -> h, request::getHeader));

        headers.put(INDEXER_QUEUE_KEY,queueKey);
       
        this.addFromMap(headers);

        // Add Correlation ID if missing
        this.addCorrelationIdIfMissing();

    }
}
