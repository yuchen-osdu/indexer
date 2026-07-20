Feature: Basic Indexing of Documents
  This feature deals with basic validation of documents in Elastic Search ingested with different kinds and attributes.

  @default
  Scenario Outline: Ingest the record and Index in the Elastic Search
    Given the schema is created with the following kind
      | kind   | index   | schemaFile   |
      | <kind> | <index> | <schemaFile> |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should get the <number> documents for the <index> in the Elastic Search
    Then I should get the elastic <mapping> for the <kind> and <index> in the Elastic Search
    Then I can validate indexed meta attributes for the <index> and given <kind>

    Examples:
      | kind                                           | recordFile               | number | index                                          | schemaFile      | acl                            | mapping                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
      | "tenant1:indexer<timestamp>:test-data--Integration:1.1.0" | "index_records_schema_1" | 5      | "tenant1-indexer<timestamp>-test-data--integration-1.1.0" | index_records_1 | "data.default.viewers@tenant1" | {"mappings":{"well":{"dynamic":"false","properties":{"acl":{"properties":{"owners":{"type":"keyword"},"viewers":{"type":"keyword"}}},"ancestry":{"properties":{"parents":{"type":"keyword"}}},"data":{"properties":{"Basin":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"Country":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"County":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"EmptyAttribute":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"RelatedRecordId":{"type":"keyword"},"ExternalIds":{"type":"keyword"},"Established":{"type":"date"},"Field":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"Location":{"type":"geo_point"},"OriginalOperator":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"Rank":{"type":"integer"},"Score":{"type":"integer"},"State":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"WellName":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"WellStatus":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"WellType":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"DblArray":{"type":"double"},"TextArray":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}}}},"id":{"type":"keyword"},"index":{"properties":{"lastUpdateTime":{"type":"date"},"statusCode":{"type":"integer"},"trace":{"type":"text"}}},"kind":{"type":"keyword"},"legal":{"properties":{"legaltags":{"type":"keyword"},"otherRelevantDataCountries":{"type":"keyword"},"status":{"type":"keyword"}}},"namespace":{"type":"keyword"},"type":{"type":"keyword"},"authority":{"type":"constant_keyword","value":"<authority-id>"},"createTime":{"type":"date"},"createUser":{"type":"keyword"},"modifyTime":{"type":"date"},"modifyUser":{"type":"keyword"},"source":{"type":"constant_keyword","value":"<source-id>"},"version":{"type":"long"},"x-acl":{"type":"keyword"}}}}} |
      | "tenant1:indexer<timestamp>:test-data--Integration:3.0.1" | "index_records_schema_3" | 7      | "tenant1-indexer<timestamp>-test-data--integration-3.0.1" | index_records_3 | "data.default.viewers@tenant1" | {"mappings":{"well":{"dynamic":"false","properties":{"acl":{"properties":{"owners":{"type":"keyword"},"viewers":{"type":"keyword"}}},"ancestry":{"properties":{"parents":{"type":"keyword"}}},"data":{"properties":{"Basin":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"Country":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"County":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"EmptyAttribute":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"Established":{"type":"date"},"Field":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"Location":{"type":"geo_point"},"OriginalOperator":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"Rank":{"type":"integer"},"Score":{"type":"integer"},"State":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"WellName":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"WellStatus":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"WellType":{"type":"text","fields":{"keyword":{"type":"keyword","null_value":"null","ignore_above":256}}},"DblArray":{"type":"double"}}},"id":{"type":"keyword"},"index":{"properties":{"lastUpdateTime":{"type":"date"},"statusCode":{"type":"integer"},"trace":{"type":"text"}}},"kind":{"type":"keyword"},"legal":{"properties":{"legaltags":{"type":"keyword"},"otherRelevantDataCountries":{"type":"keyword"},"status":{"type":"keyword"}}},"namespace":{"type":"keyword"},"type":{"type":"keyword"},"authority":{"type":"constant_keyword","value":"<authority-id>"},"createTime":{"type":"date"},"createUser":{"type":"keyword"},"modifyTime":{"type":"date"},"modifyUser":{"type":"keyword"},"source":{"type":"constant_keyword","value":"<source-id>"},"version":{"type":"long"},"x-acl":{"type":"keyword"}}}}}                                                                                                            |

  @default
  Scenario Outline: Ingest the record and Index in the Elastic Search with bad attribute
    Given the schema is created with the following kind
      | kind                                           | index                                          | schemaFile      |
      | tenant1:indexer<timestamp>:test-data--Integration:2.0.1 | tenant1-indexer<timestamp>-test-data--integration-2.0.1 | index_records_2 |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should get the <number> documents for the <index> in the Elastic Search with out <skippedAttribute>

    Examples:
      | kind                                           | recordFile        | number | index                                          | skippedAttribute      | acl                            |
      | "tenant1:indexer<timestamp>:test-data--Integration:2.0.1" | "index_records_2" | 4      | "tenant1-indexer<timestamp>-test-data--integration-2.0.1" | "data.Location"       | "data.default.viewers@tenant1" |
      | "tenant1:indexer<timestamp>:test-data--Integration:2.0.1" | "index_records_2" | 1      | "tenant1-indexer<timestamp>-test-data--integration-2.0.1" | "data.InvalidInteger" | "data.default.viewers@tenant1" |

  @default
  Scenario Outline: Ingest the record and Index in the Elastic Search with tags
    Given the schema is created with the following kind
      | kind                                           | index                                          | schemaFile      |
      | tenant1:indexer<timestamp>:test-data--Integration:1.1.1 | tenant1-indexer<timestamp>-test-data--integration-1.1.1 | index_records_1 |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should be able to search <number> record with index <index> by tag <tagKey> and value <tagValue>

    Examples:
      | kind                                           | recordFile               | index                                          | acl                            | tagKey    | tagValue    | number |
      | "tenant1:indexer<timestamp>:test-data--Integration:1.1.1" | "index_records_schema_1" | "tenant1-indexer<timestamp>-test-data--integration-1.1.1" | "data.default.viewers@tenant1" | "testtag" | "testvalue" | 5      |

  @default
  Scenario Outline: Ingest the r3-record with geo-shape and Index in the Elastic Search
    Given the schema is created with the following kind
      | kind                                      | index                                     | schemaFile                 |
      | tenant1:wks<timestamp>:master-data--Wellbore:2.0.3 | tenant1-wks<timestamp>-master-data--wellbore-2.0.3 | r3-index_record_wks_master |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should be able search <number> documents for the <index> by bounding box query with points (<top_left_latitude>, <top_left_longitude>) and  (<bottom_right_latitude>, <bottom_right_longitude>) on field <field>

    Examples:
      | kind                                      | recordFile                   | number | index                                     | acl                            | field                                   | top_left_latitude | top_left_longitude | bottom_right_latitude | bottom_right_longitude |
      | "tenant1:wks<timestamp>:master-data--Wellbore:2.0.3" | "r3-index_record_wks_master" | 1      | "tenant1-wks<timestamp>-master-data--wellbore-2.0.3" | "data.default.viewers@tenant1" | "data.SpatialLocation.Wgs84Coordinates" | 52                | -100               | 0                     | 100                    |

  @default
  Scenario Outline: Ingest record and check for properly parsed array of Strings
    Given the schema is created with the following kind
      | kind                                                           | index                                                          | schemaFile                                 |
      | osdu:wks<timestamp>:reference-data--IndexPropertyPathConfiguration:1.1.1 | osdu-wks<timestamp>-reference-data--indexpropertypathconfiguration-1.1.1 | osdu_wks_IndexPropertyPathConfiguration_v1_basic |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should be able to search for record from <index> by <field> for value <value> and find String arrays in <arrayField> with <arrayValue>

    Examples:
      | kind                                                            | recordFile                     | index                                                          | field                               | value                        | acl                            | arrayField                                                              | arrayValue                                         |
      | "osdu:wks<timestamp>:reference-data--IndexPropertyPathConfiguration:1.1.1" | "well_augmenter_configuration" | "osdu-wks<timestamp>-reference-data--indexpropertypathconfiguration-1.1.1" | "data.AttributionAuthority.keyword" | "CustomAttributionAuthority" | "data.default.viewers@tenant1" | "data.Configurations.Paths.RelatedObjectsSpec.RelatedConditionMatches" | "opendes:reference-data--GeoPoliticalEntityType:Country:" |

  @default
  Scenario Outline: Ingest records with geo-shape and Index with virtual properties in the Elastic Search
    Given the schema is created with the following kind
      | kind                                                   | index                                                  | schemaFile                      |
      | tenant1:indexer<timestamp>:virtual-properties-Integration:1.0.0 | tenant1-indexer<timestamp>-virtual-properties-integration-1.0.0 | index_record_virtual_properties |
      | tenant1:indexer<timestamp>:decimation-Integration:1.0.0         | tenant1-indexer<timestamp>-decimation-integration-1.0.0         | index_record_seismic_survey     |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should be able search <number> documents for the <index> by bounding box query with points (<top_left_latitude>, <top_left_longitude>) and  (<bottom_right_latitude>, <bottom_right_longitude>) on field <field>

    Examples:
      | kind                                                    | recordFile                        | number | index                                                  | acl                            | field                                                      | top_left_latitude | top_left_longitude | bottom_right_latitude | bottom_right_longitude |
      | "tenant1:indexer<timestamp>:virtual-properties-Integration:1.0.0" | "index_record_virtual_properties" | 3      | "tenant1-indexer<timestamp>-virtual-properties-integration-1.0.0" | "data.default.viewers@tenant1" | "data.VirtualProperties.DefaultLocation.Wgs84Coordinates"  | 90                | -180               | -90                   | 180                    |
      | "tenant1:indexer<timestamp>:decimation-Integration:1.0.0"        | "index_record_seismic_survey"     | 1      | "tenant1-indexer<timestamp>-decimation-integration-1.0.0"         | "data.default.viewers@tenant1" | "data.VirtualProperties.DefaultLocation.Wgs84Coordinates"  | 90                | -180               | -90                   | 180                    |

  @default
  Scenario Outline: Ingest the r3-record with arrays of objects and hints in schema and Index in the Elastic Search
    Given the schema is created with the following kind
      | kind                                              | index                                             | schemaFile                     |
      | tenant1:wks<timestamp>:ArraysOfObjectsTestCollection:4.0.0 | tenant1-wks<timestamp>-arraysofobjectstestcollection-4.0.0 | r3-index_record_arrayofobjects |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should be able search <number> documents for the <index> by nested <path> and properties (<first_nested_field>, <first_nested_value>) and  (<second_nested_field>, <second_nested_value>)
    Then I should be able search <number> documents for the <index> by flattened inner properties (<flattened_inner_field>, <flattened_inner_value>)
    Then I should get <object_inner_field> in response, without hints in schema for the <index> that present in the <recordFile> with <acl> for a given <kind>

    Examples:
      | kind                                              | recordFile                       | number | index                                             | acl                            | path              | first_nested_field           | first_nested_value | second_nested_field          | second_nested_value | flattened_inner_field           | flattened_inner_value | object_inner_field |
      | "tenant1:wks<timestamp>:ArraysOfObjectsTestCollection:4.0.0" | "r3-index_record_arrayofobjects" | 1      | "tenant1-wks<timestamp>-arraysofobjectstestcollection-4.0.0" | "data.default.viewers@tenant1" | "data.NestedTest" | "data.NestedTest.NumberTest" | 12345              | "data.NestedTest.StringTest" | "test string"       | "data.FlattenedTest.StringTest" | "test string"         | "ObjectTest"       |

  @default
  Scenario Outline: Synchronize meta attribute mapping on existing indexed kind
    Given the schema is created with the following kind
      | kind                                       | index                                      | schemaFile                |
      | tenant1:indexer<timestamp>:test-mapping--Sync:2.0.0 | tenant1-indexer<timestamp>-test-mapping--sync-2.0.0 | index_record_sync_mapping |
    When I create index with <mappingFile> for a given <index> and <kind>
    And  I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I can validate indexed meta attributes for the <index> and given <kind>

    Examples:
      | kind                                       | index                                      | recordFile                  | mappingFile                 | acl                            |
      | "tenant1:indexer<timestamp>:test-mapping--Sync:2.0.0" | "tenant1-indexer<timestamp>-test-mapping--sync-2.0.0" | "index_record_sync_mapping" | "index_record_sync_mapping" | "data.default.viewers@tenant1" |

  @default
  Scenario Outline: Ingest the record and Index in the Elastic Search
    Given the schema is created with the following kind
      | kind                                               | index                                              | schemaFile                   |
      | tenant1:indexer<timestamp>:test-update-data--Integration:1.0.1 | tenant1-indexer<timestamp>-test-update-data--integration-1.0.1 | index_update_records_kind_v1 |
      | tenant1:indexer<timestamp>:test-update-data--Integration:2.0.1 | tenant1-indexer<timestamp>-test-update-data--integration-2.0.1 | index_update_records_kind_v2 |
    When I ingest records with the <recordFile> with <acl> for a given <kind_v1>
    Then I should get the 1 documents for the <index_v1> in the Elastic Search
    Then I ingest records with the <recordFile> with <acl> for a given <kind_v2>
    Then I should get the 1 documents for the <index_v2> in the Elastic Search
    Then I should not get any documents for the <index_v1> in the Elastic Search

    Examples:
      | kind_v1                                               | index_v1                                              | recordFile                     | acl                            | kind_v2                                               | index_v2                                              |
      | "tenant1:indexer<timestamp>:test-update-data--Integration:1.0.1" | "tenant1-indexer<timestamp>-test-update-data--integration-1.0.1" | "index_update_records_kind_v1" | "data.default.viewers@tenant1" | "tenant1:indexer<timestamp>:test-update-data--Integration:2.0.1" | "tenant1-indexer<timestamp>-test-update-data--integration-2.0.1" |
