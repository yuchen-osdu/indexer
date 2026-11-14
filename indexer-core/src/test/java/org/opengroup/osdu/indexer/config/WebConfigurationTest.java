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

package org.opengroup.osdu.indexer.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class WebConfigurationTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
        .addFilters(new WebConfiguration().trailingSlashFilter().getFilter())
        .build();

    @ParameterizedTest
    @ValueSource(strings = {"/reindex", "/reindex/"})
    void shouldPreserveBodyAndQueryWithEitherTrailingSlash(String path) throws Exception {
        mvc.perform(post(path).queryParam("force_clean", "true")
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"a:b:c:1.0.0\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string("true:{\"kind\":\"a:b:c:1.0.0\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/index/opendes:wks:1", "/index/opendes:wks:1/"})
    void shouldMatchPathVariableEndpointWithEitherTrailingSlash(String path) throws Exception {
        mvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(content().string("opendes:wks:1"));
    }

    // Non-static inner class, so component scans in other tests (e.g. integTest.RecordIndexerApiTest) skip it
    @RestController
    class ProbeController {
        @PostMapping("/reindex")
        public String reindex(@RequestParam("force_clean") String forceClean, @RequestBody String body) {
            return forceClean + ":" + body;
        }

        @GetMapping("/index/{kind}")
        public String index(@PathVariable("kind") String kind) {
            return kind;
        }
    }
}
