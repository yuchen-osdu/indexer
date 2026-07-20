// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.indexer.azure;

import org.opengroup.osdu.indexer.IndexerApplication;
import org.opengroup.osdu.indexer.ServerletInitializer;
import org.opengroup.osdu.indexer.service.ElasticSettingServiceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;


@SpringBootApplication(exclude = { SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class })
@ComponentScan(
        basePackages = {"org.opengroup.osdu"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value=IndexerApplication.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value=ServerletInitializer.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value=ElasticSettingServiceImpl.class),
        }
)
@PropertySource("classpath:swagger.properties")
public class IndexerAzureApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndexerAzureApplication.class, args);
    }

}
