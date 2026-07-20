Feature: check the api key in header
  This feature check the api key received from headers is matching with configured in environment

  Scenario Outline: compare the api key with configured environment key
    When I pass api key
    Then compare with key configured in properties file

    Examples:
      | apiKey    | 
      | "abcd" 	  | 
