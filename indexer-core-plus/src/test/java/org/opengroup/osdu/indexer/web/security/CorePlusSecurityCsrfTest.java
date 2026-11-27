/*
 * Copyright 2020-2022 Google LLC
 * Copyright 2020-2022 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package org.opengroup.osdu.indexer.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {CorePlusSecurityConfig.class, CorePlusSecurityCsrfTest.MvcConfiguration.class})
class CorePlusSecurityCsrfTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void shouldNotInstallCsrfOrBrowserBasicAuthenticationForStatelessApi() {
        var filters = securityFilterChain.getFilters("/csrf-test");

        assertFalse(filters.stream().anyMatch(CsrfFilter.class::isInstance));
        assertFalse(filters.stream().anyMatch(BasicAuthenticationFilter.class::isInstance));
    }

    @Test
    void shouldAllowStatelessBearerApiRequestsWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/csrf-test").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfiguration {

        @Bean
        CsrfTestController csrfTestController() {
            return new CsrfTestController();
        }
    }

    @RestController
    static class CsrfTestController {

        @PostMapping("/csrf-test")
        String post() {
            return "ok";
        }
    }
}
