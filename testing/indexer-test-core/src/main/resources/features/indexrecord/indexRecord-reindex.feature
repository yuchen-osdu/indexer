Feature: Reindex Functionality
  This feature deals with validation of reindexing functionality for documents in Elastic Search.

  @reindex
  Scenario Outline: Reindex records by kind and validate in Elastic Search
    Given the schema is created with the following kind
      | kind   | index   | schemaFile   |
      | <kind> | <index> | <schemaFile> |
    Given I have ingested records with the <recordFile> with <acl> for a given <kind>
    When I prepare missing documents before reindex for <index>
    And I trigger reindex for the <kind> with cursor <cursor>
    Then I should get successful reindex response
    And I should verify reindexed documents are present in the <index> in Elastic Search

    Examples:
      | kind                                           | recordFile               | index                                          | schemaFile                 | acl                            | cursor |
      | "tenant1:indexer<timestamp>:test-data--Integration:1.1.1" | "index_records_1_reindex" | "tenant1-indexer<timestamp>-test-data--integration-1.1.1" | index_records_1_reindex            | "data.default.viewers@tenant1" | ""     |
      | "tenant1:indexer<timestamp>:test-data--Integration:3.0.2" | "index_records_3_reindex" | "tenant1-indexer<timestamp>-test-data--integration-3.0.2" | index_records_3_reindex            | "data.default.viewers@tenant1" | ""     |
      | "tenant1:wks<timestamp>:master-data--Wellbore:2.0.4"     | "r3-index_record_wks_master_reindex" | "tenant1-wks<timestamp>-master-data--wellbore-2.0.4"    | r3-index_record_wks_master_reindex | "data.default.viewers@tenant1" | ""     |

  @reindex @error-handling
  Scenario Outline: Reindex records with cursor pagination - invalid cursor returns error
    Given the schema is created with the following kind
      | kind                                           | index                                          | schemaFile      |
      | tenant1:indexer<timestamp>:test-data--Integration:1.1.1 | tenant1-indexer<timestamp>-test-data--integration-1.1.1 | index_records_1_reindex |
    Given I have ingested records with the <recordFile> with <acl> for a given <kind>
    When I prepare missing documents before reindex for <index>
    And I trigger reindex for the <kind> with cursor <initial_cursor>
    Then I should get successful reindex response
    And I should verify reindexed documents are present in the <index> in Elastic Search
    When I trigger reindex for the <kind> with cursor <next_cursor>
    Then I should get error response with status code 400

    Examples:
      | kind                                           | recordFile               | index                                          | acl                            | initial_cursor | next_cursor      |
      | "tenant1:indexer<timestamp>:test-data--Integration:1.1.1" | "index_records_1_reindex" | "tenant1-indexer<timestamp>-test-data--integration-1.1.1" | "data.default.viewers@tenant1" | ""             | "batch-cursor-1" |

  @reindex
  Scenario Outline: Reindex records with force clean enabled
    Given the schema is created with the following kind
      | kind   | index   | schemaFile   |
      | <kind> | <index> | <schemaFile> |
    Given I have ingested records with the <recordFile> with <acl> for a given <kind>
    When I prepare missing documents before reindex for <index>
    And I trigger reindex for the <kind> with force_clean enabled
    Then I should get successful reindex response
    And I should verify reindexed documents are present in the <index> in Elastic Search

    Examples:
      | kind                                           | recordFile               | index                                          | schemaFile      | acl                            |
      | "tenant1:indexer<timestamp>:test-data--Integration:4.0.1" | "index_records_4_reindex"        | "tenant1-indexer<timestamp>-test-data--integration-4.0.1" | index_records_4_reindex | "data.default.viewers@tenant1" |

  @reindex
  Scenario Outline: Reindex specific records by record IDs with dynamic extraction
    Given the schema is created with the following kind
      | kind                                           | index                                          | schemaFile      |
      | tenant1:indexer<timestamp>:test-data--Integration:1.1.1 | tenant1-indexer<timestamp>-test-data--integration-1.1.1 | index_records_1_reindex |
    Given I have ingested records with the <recordFile> with <acl> for a given <kind>
    When I prepare missing documents before reindex for <index>
    And I trigger reindex for dynamic record IDs
    Then I should get successful reindex response
    And I should verify the specific records are reindexed in the <index> in Elastic Search

    Examples:
      | kind                                           | recordFile               | index                                          | acl                            |
      | "tenant1:indexer<timestamp>:test-data--Integration:1.1.1" | "index_records_1_reindex" | "tenant1-indexer<timestamp>-test-data--integration-1.1.1" | "data.default.viewers@tenant1" |

  @reindex @error-handling
  Scenario Outline: Reindex with invalid kind should return error
    When I trigger reindex for invalid <invalid_kind> with cursor <cursor>
    Then I should get error response with status code 400
    And the error message should indicate invalid kind format

    Examples:
      | invalid_kind      | cursor |
      | "invalid-kind"    | ""     |
      | ""                | ""     |
      | "malformed:kind"  | ""     |
