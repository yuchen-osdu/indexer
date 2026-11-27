/*
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.indexer.web.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.indexer.config.WebConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * Runs requests through the real Spring Security 7 filter chain built from {@link CorePlusSecurityConfig}.
 */
class SecurityFirewallTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DispatcherServletAutoConfiguration.class,
                    WebMvcAutoConfiguration.class))
            .withUserConfiguration(CorePlusSecurityConfig.class)
            .withBean(FirewallProbeController.class, FirewallProbeController::new);

    @Test
    void shouldPermitRequestsWithoutCredentials() {
        runner.run(context -> mockMvc(context)
                .perform(get("/firewall-probe/abc"))
                .andExpect(status().isOk())
                .andExpect(content().string("abc")));
    }

    @Test
    void shouldReachHandlerWithTrailingSlashThroughSecurityChain() {
        runner.run(context -> mockMvc(context)
                .perform(get("/firewall-probe/abc/"))
                .andExpect(status().isOk())
                .andExpect(content().string("abc")));
    }

    @Test
    void shouldRejectEncodedSlash() {
        runner.run(context -> mockMvc(context)
                .perform(get(URI.create("/firewall-probe/a%2Fb")))
                .andExpect(status().isBadRequest()));
    }

    private static MockMvc mockMvc(WebApplicationContext context) {
        UrlHandlerFilter trailingSlashFilter = new WebConfiguration().trailingSlashFilter().getFilter();
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(trailingSlashFilter)
                .apply(springSecurity())
                .build();
    }

    // Non-static inner class, so component scans in other tests skip it
    @RestController
    class FirewallProbeController {
        @GetMapping("/firewall-probe/{id}")
        String probe(@PathVariable("id") String id) {
            return id;
        }
    }
}
