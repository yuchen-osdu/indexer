# Environment setup for developers

##Checklist: You should have done following setup before running the integration tests:
1) Create Search Integration test service account
2) Setup Search Integration test account in Entitlement service with following groups:

    i. service.search.user
    
    ii. service.entitlements.user
    
    ii. users@{tenant1}@{domain}.com
3) Create data group, add the service account to that group and substitute **DEFAULT_DATA_GROUP** in Config file
4) Create a valid legal tag (eg: my-legal-tag) with a other relevant data countries (eg: US) and update **DEFAULT_LEGAL_TAG** and **DEFAULT_OTHER_RELEVANT_DATA_COUNTRIES** variables on config files respectively.
5) Update **DEFAULT_SEARCH_INTEGRATION_TESTER** variable in Config file with base64 encoded value to service account json key
6) Update **DEFAULT_SEARCH_ON_BEHALF_INTEGRATION_TESTER** variable in Config file with base64 encoded value to service account json key (it will be used for slb-on-behalf-header)
7) Have credentials for Elastic Cluster and update **DEFAULT_ELASTIC_HOST**, **DEFAULT_ELASTIC_USER_NAME** and **DEFAULT_ELASTIC_PASSWORD**.

Note: 
1) Config (Config.java) file is present in org.opengroup.osdu.util package
2) Do not add the service account to tenant2 (in Entitlements)

## Step 1:
Import the project using maven and maven should resolve all the dependencies automatically

## Step 2:
Install [Lombok plugin](https://projectlombok.org/setup/intellij)

## Step 3:
Add the search cluster settings to Config.java
 
## Step 4:
Execute following command to build code and run all the integration tests:
```
mvn clean install -P integration-test
```

#How to write a new Integration test?
1) Create a Feature file in resources/features folder
    ```
    A Feature File is an entry point to the Cucumber tests. This is a file where you will describe your tests 
    in Descriptive language (Like English). It is an essential part of Cucumber, as it serves as an automation test script as well as live documents. 
    A feature file can contain a scenario or can contain many scenarios in a single feature file but it usually contains a list of scenarios.
    ```
2) Run the feature file and it will generate blank stubs
3) Copy the stubs
4) Create step definition class for the feature
    ```
    A Step Definition is a Java method with an expression that links it to one or more Gherkin steps. When Cucumber executes a Gherkin step in a scenario, 
    it will look for a matching step definition to execute.
    ```
5) Paste the empty stubs in step definition class and write its implementations
6) Create RunTest.java file illustrating the link between feature file and the step definition

####To illustrate how this works, look at the following Gherkin Scenario:

```
 Scenario: Some cukes
    Given I have 48 cukes in my belly
```

The `I have 48 cukes in my belly` part of the step (the text following the `Given` keyword) will match the following step definition:

```
package foo;
import cucumber.api.java.en.Given;

public class MyStepdefs {
    @Given("I have (\\d+) cukes in my belly")
    public void i_have_n_cukes_in_my_belly(int cukes) {
        System.out.format("Cukes: %n\n", cukes);
    }
}
```
