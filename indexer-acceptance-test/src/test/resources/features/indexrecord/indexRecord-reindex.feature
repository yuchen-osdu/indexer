Feature: Reindex Functionality
  This feature validates the reindex v1 endpoint against a live OSDU environment.

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
      | kind                                                      | recordFile                | index                                                     | schemaFile              | acl                            | cursor |
      | "tenant1:indexer<timestamp>:test-data--Integration:1.1.1" | "index_records_1_reindex" | "tenant1-indexer<timestamp>-test-data--integration-1.1.1" | index_records_1_reindex | "data.default.viewers@tenant1" | ""     |

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
      | kind                                                      | recordFile                | index                                                     | schemaFile              | acl                            |
      | "tenant1:indexer<timestamp>:test-data--Integration:4.0.1" | "index_records_4_reindex" | "tenant1-indexer<timestamp>-test-data--integration-4.0.1" | index_records_4_reindex | "data.default.viewers@tenant1" |

  @reindex @error-handling
  Scenario Outline: Reindex with invalid kind should return error
    When I trigger reindex for invalid <invalid_kind> with cursor <cursor>
    Then I should get error response with status code 400

    Examples:
      | invalid_kind   | cursor |
      | "invalid-kind" | ""     |
      | ""             | ""     |
