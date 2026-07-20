Feature: Extended Properties Indexing
  This feature validates extended properties and index property configuration in Elastic Search.
  Each scenario creates its own unique kinds with timestamps to avoid conflicts.

  @indexer-extended
  Scenario: Test Wellbore Extended Properties from NameAliases
    Given the schema is created with the following kind
      | kind                                     | index                                            | schemaFile                            |
      | osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.0 | osdu-wks-reference-data--indexpropertypathconfiguration-1.1.0 | osdu_wks_IndexPropertyPathConfiguration_v1_extended |
      | test:indexer<timestamp>:wellbore:1.1.0 | test-indexer<timestamp>-wellbore-1.1.0         | index-property-wellbore_v1_extended                  |
    When I ingest records with the "osdu_wks_IndexPropertyPathConfiguration_wellbore_v1_extended" with "data.default.viewers@tenant1" for a given "osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.0"
    Then I should be able to search 1 record with index "osdu-wks-reference-data--indexpropertypathconfiguration-1.1.0" by extended data field "data.Code" and value "wellbore"
    When I ingest records with the "index-property-wellbore_v1_extended" with "data.default.viewers@tenant1" for a given "test:indexer<timestamp>:wellbore:1.1.0"
    Then I should be able to search 1 record with index "test-indexer<timestamp>-wellbore-1.1.0" by extended data field "data.WellUWI" and value "123454321"
    And I should be able search 1 documents for the "test-indexer<timestamp>-wellbore-1.1.0" by bounding box query with points (30, -96) and  (29, -95) on field "data.Location"

  @indexer-extended
  Scenario: Test WellLog Extended Properties from Parent Wellbore
    Given the schema is created with the following kind
      | kind                                     | index                                            | schemaFile                            |
      | osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.0 | osdu-wks-reference-data--indexpropertypathconfiguration-1.1.0 | osdu_wks_IndexPropertyPathConfiguration_v1_extended |
      | test:indexer<timestamp>:wellbore:1.1.0 | test-indexer<timestamp>-wellbore-1.1.0         | index-property-wellbore_v1_extended                  |
      | test:indexer<timestamp>:welllog:1.0.0  | test-indexer<timestamp>-welllog-1.0.0          | index-property-welllog_v1_extended                   |
    When I ingest records with the "osdu_wks_IndexPropertyPathConfiguration_welllog_v1_extended" with "data.default.viewers@tenant1" for a given "osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.0"
    Then I should be able to search 1 record with index "osdu-wks-reference-data--indexpropertypathconfiguration-1.1.0" by extended data field "data.Code" and value "welllog"
    When I ingest records with the "index-property-wellbore_v1_extended" with "data.default.viewers@tenant1" for a given "test:indexer<timestamp>:wellbore:1.1.0"
    Then I should get the 1 documents for the "test-indexer<timestamp>-wellbore-1.1.0" in the Elastic Search
    When I ingest records with the "index-property-welllog_v1_extended" with "data.default.viewers@tenant1" for a given "test:indexer<timestamp>:welllog:1.0.0"
    Then I should be able to search 1 record with index "test-indexer<timestamp>-welllog-1.0.0" by extended data field "data.WellboreName" and value "Facility_123"
    And I should be able search 1 documents for the "test-indexer<timestamp>-welllog-1.0.0" by bounding box query with points (30, -96) and  (29, -95) on field "data.SpatialLocation"

  @indexer-extended
  Scenario: Test BinGrid Extended Properties with Area and Extent functions
    Given the schema is created with the following kind
      | kind                                     | index                                            | schemaFile                            |
      | osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.0 | osdu-wks-reference-data--indexpropertypathconfiguration-1.1.0 | osdu_wks_IndexPropertyPathConfiguration_v1_extended |
      | test:indexer<timestamp>:bingrid:1.1.0  | test-indexer<timestamp>-bingrid-1.1.0          | index_property_bingrid_v1_extended                   |
    When I ingest records with the "osdu_wks_IndexPropertyPathConfiguration_bingrid_v1_extended" with "data.default.viewers@tenant1" for a given "osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.0"
    Then I should be able to search 1 record with index "osdu-wks-reference-data--indexpropertypathconfiguration-1.1.0" by extended data field "data.Code" and value "bingrid"
    When I ingest records with the "index_property_bingrid_v1_extended" with "data.default.viewers@tenant1" for a given "test:indexer<timestamp>:bingrid:1.1.0"
    Then I should be able to search 1 record with index "test-indexer<timestamp>-bingrid-1.1.0" by extended data field "data.Area" and value "4.496916513E7"

  @indexer-extended
  Scenario: Test LineGeometry Extended Properties with Length and Extent functions
    Given the schema is created with the following kind
      | kind                                     | index                                            | schemaFile                            |
      | osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.0 | osdu-wks-reference-data--indexpropertypathconfiguration-1.1.0 | osdu_wks_IndexPropertyPathConfiguration_v1_extended |
      | test:indexer<timestamp>:linegeometry:1.1.0 | test-indexer<timestamp>-linegeometry-1.1.0   | index_property_linegeometry_v1_extended               |
    When I ingest records with the "osdu_wks_IndexPropertyPathConfiguration_linegeometry_v1_extended" with "data.default.viewers@tenant1" for a given "osdu:wks:reference-data--IndexPropertyPathConfiguration:1.1.0"
    Then I should be able to search 1 record with index "osdu-wks-reference-data--indexpropertypathconfiguration-1.1.0" by extended data field "data.Code" and value "linegeometry"
    When I ingest records with the "index_property_linegeometry_v1_extended" with "data.default.viewers@tenant1" for a given "test:indexer<timestamp>:linegeometry:1.1.0"
    Then I should be able to search 1 record with index "test-indexer<timestamp>-linegeometry-1.1.0" by extended data field "data.Length" and value "12021.88"
