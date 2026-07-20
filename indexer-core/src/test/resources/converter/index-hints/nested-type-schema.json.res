{
  "kind": "osdu:osdu:Wellbore:1.0.0",
  "schema": [
    {
      "path": "Description",
      "kind": "string"
    },
    {
      "path": "keywords",
      "kind": "string"
    },
    {
      "path": "originalLocation",
      "kind": "string"
    },
    {
      "path": "wellboreId",
      "kind": "string"
    },
    {
      "path": "classification.summary",
      "kind": "string"
    },
    {
      "path": "classification.petro.inferred",
      "kind": "[]string"
    },
    {
      "path": "classification.petro.accepted",
      "kind": "[]string"
    },
    {
      "path": "classification.petro.geopoliticalContext",
      "kind": "nested",
      "properties": [
        {
          "path": "country",
          "kind": "string"
        },
        {
          "path": "taxNode",
          "kind": "string"
        },
        {
          "path": "country_region",
          "kind": "string"
        },
        {
          "path": "region",
          "kind": "string"
        }
      ]
    },
    {
      "path": "classification.petro.otherTerms",
      "kind": "[]string"
    },
    {
      "path": "classification.taxNodes",
      "kind": "nested",
      "properties": [
        {
          "path": "score",
          "kind": "double"
        },
        {
          "path": "taxNode",
          "kind": "string"
        },
        {
          "path": "taxonomy",
          "kind": "string"
        },
        {
          "path": "explanation",
          "kind": "string"
        },
        {
          "path": "cf-score",
          "kind": "int"
        }
      ]
    },
    {
      "path": "classification.concept-tags",
      "kind": "nested",
      "properties": [
        {
          "path": "score",
          "kind": "double"
        },
        {
          "path": "concept-tag",
          "kind": "string"
        },
        {
          "path": "ttype",
          "kind": "string"
        }
      ]
    },
    {
      "path": "classification.title",
      "kind": "string"
    },
    {
      "path": "Source",
      "kind": "string"
    },
    {
      "path": "Name",
      "kind": "string"
    },
    {
      "path": "size",
      "kind": "string"
    },
    {
      "path": "extractedTextFilePath",
      "kind": "string"
    },
    {
      "path": "thumbnailPath",
      "kind": "string"
    },
    {
      "path": "SubTitle",
      "kind": "string"
    }
  ]
}

