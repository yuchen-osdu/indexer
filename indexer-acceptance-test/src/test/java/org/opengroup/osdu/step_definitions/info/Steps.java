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

package org.opengroup.osdu.step_definitions.info;

import io.cucumber.java.Scenario;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.opengroup.osdu.common.info.InfoSteps;
import org.opengroup.osdu.util.AnthosHTTPClient;
import org.opengroup.osdu.util.Config;

public class Steps extends InfoSteps {

    public Steps() {
        super(new AnthosHTTPClient());
    }

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
        this.httpClient = new AnthosHTTPClient();
    }

    /******************Inputs being set**************/

    @When("^I send get request to version info endpoint$")
    public void i_send_get_request_to_version_info_endpoint() {
        super.i_send_get_request_to_version_info_endpoint();
    }

    @When("^I send get request to version info endpoint with trailing slash$")
    public void i_send_get_request_to_version_info_endpoint_with_trailing_slash() {
        super.i_send_get_request_to_version_info_endpoint_with_trailing_slash();
    }

    /******************Assert final response**************/

    @Then("^I should get version info in response$")
    public void i_should_get_version_info_in_response() {
        super.i_should_get_version_info_in_response();
    }

    @Override
    protected String getHttpMethod() {
        return "GET";
    }

    @Override
    protected String getApi() {
        return Config.getIndexerBaseURL() + "info";
    }
}
