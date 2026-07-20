Feature: Extended Properties Indexing
  This feature validates extended properties and index property configuration in Elastic Search.
  Each scenario is self-contained: it creates the config, verifies it is indexed, then
  ingests data and asserts augmented fields are present. This avoids cross-scenario
  timestamp mismatches (Cucumber creates a new step instance per scenario).

  @indexer-extended
  Scenario: Test Wellbore extended string and spatial properties
    Given the schema is created with the following kind
      | kind                                                                               | index                                                                             | schemaFile                                                |
      | osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.1                       | osdu-wks-reference-data--indexpropertypathconfiguration-1.1.1                      | osdu_wks_IndexPropertyPathConfiguration_v1_extended        |
      | test:indexer<timestamp>:wellbore:1.1.0                                              | test-indexer<timestamp>-wellbore-1.1.0                                             | index-property-wellbore_v1_extended                        |
    When I ingest records with the "osdu_wks_IndexPropertyPathConfiguration_wellbore_v1_extended" with "data.default.viewers@tenant1" for a given "osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.1"
    Then I should be able to search 1 record with index "osdu-wks-reference-data--indexpropertypathconfiguration-1.1.1" by extended data field "data.Code" and value "wellbore"
    When I ingest records with the "index-property-wellbore_v1_extended" with "data.default.viewers@tenant1" for a given "test:indexer<timestamp>:wellbore:1.1.0"
    Then I should be able to search 1 record with index "test-indexer<timestamp>-wellbore-1.1.0" by extended data field "data.WellUWI" and value "123454321"
    And I should be able search 1 documents for the "test-indexer<timestamp>-wellbore-1.1.0" by bounding box query with points (30, -96) and  (29, -95) on field "data.Location"

  @indexer-extended
  Scenario: Test WellLog extended string and spatial properties from parent Wellbore
    Given the schema is created with the following kind
      | kind                                                                               | index                                                                             | schemaFile                                                |
      | osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.1                       | osdu-wks-reference-data--indexpropertypathconfiguration-1.1.1                      | osdu_wks_IndexPropertyPathConfiguration_v1_extended        |
      | test:indexer<timestamp>:wellbore:1.1.0                                              | test-indexer<timestamp>-wellbore-1.1.0                                             | index-property-wellbore_v1_extended                        |
      | test:indexer<timestamp>:welllog:1.0.0                                               | test-indexer<timestamp>-welllog-1.0.0                                              | index-property-welllog_v1_extended                         |
    When I ingest records with the "osdu_wks_IndexPropertyPathConfiguration_welllog_v1_extended" with "data.default.viewers@tenant1" for a given "osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.1"
    Then I should be able to search 1 record with index "osdu-wks-reference-data--indexpropertypathconfiguration-1.1.1" by extended data field "data.Code" and value "welllog"
    When I ingest records with the "index-property-wellbore_v1_extended" with "data.default.viewers@tenant1" for a given "test:indexer<timestamp>:wellbore:1.1.0"
    Then I should get the 1 documents for the "test-indexer<timestamp>-wellbore-1.1.0" in the Elastic Search
    When I ingest records with the "index-property-welllog_v1_extended" with "data.default.viewers@tenant1" for a given "test:indexer<timestamp>:welllog:1.0.0"
    Then I should be able to search 1 record with index "test-indexer<timestamp>-welllog-1.0.0" by extended data field "data.WellboreName" and value "Facility_123"
    And I should be able search 1 documents for the "test-indexer<timestamp>-welllog-1.0.0" by bounding box query with points (30, -96) and  (29, -95) on field "data.SpatialLocation"
