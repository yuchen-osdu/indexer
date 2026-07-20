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


package org.opengroup.osdu.indexer.util;

import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.http.ResponseHeadersFactory;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Log
@Component
public class IndexerFilter implements Filter {

    // defaults to * for any front-end, string must be comma-delimited if more than one domain
    private final String accessControlAllowOriginDomains;

    private final DpsHeaders dpsHeaders;
    private ResponseHeadersFactory responseHeadersFactory = new ResponseHeadersFactory();

    @Autowired
    public IndexerFilter(DpsHeaders dpsHeaders, @Value("${ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS:*}") final String accessControlAllowOriginDomains) {
        this.dpsHeaders = dpsHeaders;
        this.accessControlAllowOriginDomains = accessControlAllowOriginDomains;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        dpsHeaders.addCorrelationIdIfMissing();

        setResponseHeaders(httpResponse);

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private void setResponseHeaders(HttpServletResponse httpServletResponse) {
        Map<String, String> responseHeaders = responseHeadersFactory.getResponseHeaders(accessControlAllowOriginDomains);
        for (Map.Entry<String, String> header : responseHeaders.entrySet()) {
            httpServletResponse.addHeader(header.getKey(), header.getValue());
        }
        httpServletResponse.addHeader(DpsHeaders.CORRELATION_ID, this.dpsHeaders.getCorrelationId());
    }

    @Override
    public void destroy() {
    }
}
