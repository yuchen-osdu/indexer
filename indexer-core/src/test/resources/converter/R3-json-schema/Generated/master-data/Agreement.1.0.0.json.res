{
  "kind": "osdu:osdu:Wellbore:1.0.0",
  "schema": [
    {
      "kind": "string",
      "path": "AgreementIdentifier"
    },
    {
      "kind": "string",
      "path": "AgreementName"
    },
    {
      "kind": "string",
      "path": "AgreementExternalID"
    },
    {
      "kind": "string",
      "path": "AgreementExternalSystem"
    },
    {
      "kind": "link",
      "path": "AgreementParentID"
    },
    {
      "kind": "link",
      "path": "AgreementTypeID"
    },
    {
      "kind": "datetime",
      "path": "EffectiveDate"
    },
    {
      "kind": "[]link",
      "path": "Counterparties"
    },
    {
      "kind": "[]object",
      "path": "Terms"
    },
    {
      "kind": "[]object",
      "path": "RestrictedResources"
    }
  ]
}