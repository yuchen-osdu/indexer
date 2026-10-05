Feature: Elasticsearch Alias Management for Indexed Records
  This feature validates that the indexer service correctly creates and manages
  Elasticsearch aliases with versioned physical indexes, supports backward compatibility
  with legacy non-aliased indexes, and handles schema merges without recreating indexes.

  @alias
  Scenario Outline: Verify alias creation on first record indexing
    Given the schema is created with the following kind
      | kind   | index   | schemaFile   |
      | <kind> | <index> | <schemaFile> |
    When I ingest records with the "<recordFile>" with "<acl>" for a given "<kind>"
    Then I should get the 1 documents for the "<index>" in the Elastic Search
    And I verify in Elasticsearch that alias "<index>" exists and points to physical index "<index>-r1"
    And I verify in Elasticsearch that physical index "<index>" does not exist

    Examples:
      | kind                                                     | recordFile        | index                                                   | schemaFile        | acl                          |
      | tenant1:indexer<timestamp>:alias-test--Integration:1.0.0 | alias_test_record | tenant1-indexer<timestamp>-alias-test--integration-1.0.0 | alias_test_record | data.default.viewers@tenant1 |

  @alias
  Scenario Outline: Verify subsequent indexing uses existing alias
    Given the schema is created with the following kind
      | kind   | index   | schemaFile   |
      | <kind> | <index> | <schemaFile> |
    Given I have ingested records with the "<recordFile1>" with "<acl>" for a given "<kind>"
    When I ingest records with the "<recordFile2>" with "<acl>" for a given "<kind>"
    Then I should get the 2 documents for the "<index>" in the Elastic Search
    Then I should get the 2 documents for the "<index>-r1" in the Elastic Search

    Examples:
      | kind                                                      | recordFile1       | recordFile2        | index                                                    | schemaFile        | acl                          |
      | tenant1:indexer<timestamp>:alias-test--Integration:1.0.0 | alias_test_record | alias_test_record2 | tenant1-indexer<timestamp>-alias-test--integration-1.0.0 | alias_test_record | data.default.viewers@tenant1 |

  @alias
  Scenario Outline: Service deletion removes alias, physical index and cache entry
    Given the schema is created with the following kind
      | kind   | index   | schemaFile   |
      | <kind> | <index> | <schemaFile> |
    Given I have ingested records with the "<recordFile>" with "<acl>" for a given "<kind>"
    And I verify in Elasticsearch that alias "<index>" exists and points to physical index "<index>-r1"
    When I delete the index using indexer service endpoint for kind "<kind>"
    Then I verify in Elasticsearch that physical index "<index>-r1" does not exist
    And I verify in Elasticsearch that alias "<index>" does not exist
    When I ingest records with the "<recordFile>" with "<acl>" for a given "<kind>"
    Then I verify in Elasticsearch that physical index "<index>-r1" exists
    And I verify in Elasticsearch that alias "<index>" exists and points to physical index "<index>-r1"
    Then I delete the index using indexer service endpoint for kind "<kind>"

    Examples:
      | kind                                                       | recordFile        | index                                                     | schemaFile        | acl                          |
      | tenant1:indexer<timestamp>:alias-delete--Integration:1.0.0 | alias_test_record | tenant1-indexer<timestamp>-alias-delete--integration-1.0.0 | alias_test_record | data.default.viewers@tenant1 |

  @backward-compatibility @alias
  Scenario Outline: Service handles pre-existing physical indexes
    Given the schema is created with the following kind
      | kind   | index   | schemaFile   |
      | <kind> | <index> | <schemaFile> |
    Given I manually create a physical index "<index>" in Elasticsearch
    When I ingest records with the "<recordFile>" with "<acl>" for a given "<kind>"
    Then I should get the 1 documents for the "<index>" in the Elastic Search
    And I verify in Elasticsearch that alias "<index>" does not exist
    Then I delete the index using indexer service endpoint for kind "<kind>"

    Examples:
      | kind                                                 | recordFile    | index                                               | schemaFile        | acl                          |
      | tenant1:indexer<timestamp>:legacy--Integration:1.0.0 | legacy_record | tenant1-indexer<timestamp>-legacy--integration-1.0.0 | alias_test_record | data.default.viewers@tenant1 |

  @schema-merge @alias
  Scenario Outline: Schema update merges mappings for aliased index
    Given the schema is created with the following kind
      | kind   | index   | schemaFile   |
      | <kind> | <index> | <schemaFile> |
    Given I have ingested records with the "<recordFile>" with "<acl>" for a given "<kind>"
    And I verify in Elasticsearch that alias "<index>" exists and points to physical index "<index>-r1"
    Then I should get the 1 documents for the "<index>" in the Elastic Search
    When the schema is updated with the following kind
      | kind   | index   | schemaFile     |
      | <kind> | <index> | <recordFileV2> |
    Then I verify the mapping is merged in physical index "<index>-r1"
    And I verify fields "<newFields>" are present in the mapping for physical index "<index>-r1"
    And I verify fields "<existingFields>" are present in the mapping for physical index "<index>-r1"
    When I ingest records with the "<recordFileV2>" with "<acl>" for a given "<kind>"
    Then I should get the 2 documents for the "<index>" in the Elastic Search

    Examples:
      | kind                                                        | recordFile           | recordFileV2         | index                                                        | schemaFile           | acl                          | newFields                   | existingFields                          |
      | tenant1:indexer<timestamp>:revision-test--Integration:1.0.0 | schema_merge_test_v1 | schema_merge_test_v2 | tenant1-indexer<timestamp>-revision-test--integration-1.0.0 | schema_merge_test_v1 | data.default.viewers@tenant1 | NewTestField3,NewTestField4 | TestField1,TestField2,Location,WellName |
