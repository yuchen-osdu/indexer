Feature: Fetch info about maven build and git repository.

  @default
  Scenario: Verify version info endpoint content
    When I send get request to version info endpoint
    Then I should get version info in response

  @default
  Scenario: Verify version info endpoint content for request with trailing slash
    When I send get request to version info endpoint with trailing slash
    Then I should get version info in response
