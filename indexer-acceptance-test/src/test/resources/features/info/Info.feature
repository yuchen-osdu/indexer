Feature: Fetch info about maven build and git repository.

  @default
  Scenario: Verify version info endpoint content
    Given I send get request to version info endpoint
    Then service should respond back with version info in response

  @default
  Scenario: Verify version info endpoint content for request with trailing slash
    Given I send get request to version info endpoint with trailing slash
    Then service should respond back with version info in response
