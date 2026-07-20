Feature: Text Search and Special Attributes
  This feature deals with validation of text search features including collaboration, keyword lowercase, and bag of words.

  @collaboration-test
  Scenario Outline: X Ingest the record with no xcollab value and Index in the Elastic Search with tags
    Given the schema is created with the following kind
      | kind                                        | index                                        | schemaFile                 |
      | test:indexer<timestamp>:index-property--Wellbore:1.2.0 | test-indexer<timestamp>-index-property--wellbore-1.2.0 | index-property-wellbore_v1_textsearch |
    When I ingest records with xcollab value <xcollab> included with the <recordFile> with <acl> for a given <kind>
    Then I should get the <number> documents with xcollab value <xcollab> included for the <index> in the Elastic Search

    Examples:
      | kind                                          | recordFile                   | number | index                                         | acl                            | field                            | value          | xcollab                                                   |
      | "test:indexer<timestamp>:index-property--Wellbore:1.2.0" | "index-property-wellbore_v1_textsearch" | 1      | "test-indexer<timestamp>-index-property--wellbore-1.2.0" | "data.default.viewers@tenant1" | "data.FacilityName.keywordLower" | "facility_456" | "id=a99cef48-2ed6-4beb-8a43-002373431f92,application=pws" |

  @keyword-lower
  Scenario Outline: Ingest record and Index in the Elastic Search with keywordLower attribute for text fields
    Given the schema is created with the following kind
      | kind                                        | index                                        | schemaFile                 |
      | test:indexer<timestamp>:index-property--Wellbore:1.2.0 | test-indexer<timestamp>-index-property--wellbore-1.2.0 | index-property-wellbore_v1_textsearch |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should be able to search <number> record with index <index> by extended data field <field> and value <value>

    Examples:
      | kind                                           | recordFile                    | number | index                                           | acl                            |  field                            | value           |
      | "test:indexer<timestamp>:index-property--Wellbore:1.2.0"  | "index-property-wellbore_v1_textsearch"  | 1      |  "test-indexer<timestamp>-index-property--wellbore-1.2.0"  | "data.default.viewers@tenant1" | "data.FacilityName.keywordLower"  | "facility_123"  |

  @bag-of-words
  Scenario Outline: Ingest record and Index bag of words as an attribute
    Given the schema is created with the following kind
      | kind                                        | index                                        | schemaFile                 |
      | test:indexer<timestamp>:index-property--Wellbore:1.2.0 | test-indexer<timestamp>-index-property--wellbore-1.2.0 | index-property-wellbore_v1_textsearch |
    When I ingest records with the <recordFile> with <acl> for a given <kind>
    Then I should be able to search <number> record with index <index> by extended data field <field> and value <value>

    Examples:
      | kind                                           | recordFile                    | number | index                                           | acl                            |  field                            | value           |
      | "test:indexer<timestamp>:index-property--Wellbore:1.2.0"  | "index-property-wellbore_v1_textsearch"  | 1      |  "test-indexer<timestamp>-index-property--wellbore-1.2.0"  | "data.default.viewers@tenant1" | "bagOfWords"  | "facility_123"  |
