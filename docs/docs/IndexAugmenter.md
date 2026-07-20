# Index Augmenter/Extensions

## Introduction

In this document, the terms `index augmenter` and `index extensions` are interchangeably used to describe this indexer feature.

OSDU Standard index extensions are defined by OSDU Data Definition work-streams with the intent to provide
user/application friendly, derived properties. The standard set, together with the OSDU schemas, form the
interoperability foundation. They can contribute to deliver domain specific APIs according to the Domain Driven Design
principles. 

The configurations are encoded in OSDU reference-data records, one per each major schema version. The type name
is IndexPropertyPathConfiguration. The diagram below shows the decomposition into parts.

![IndexPropertyPathConfiguration](images/IndexPropertyPathConfiguration.png)

* One IndexPropertyPathConfiguration record corresponds to one schema kind's major version, i.e., the
  IndexPropertyPathConfiguration record id for all the `schema osdu:wks:master-data--Wellbore:1.*.*` kinds is set
  to `partition-id:reference-data--IndexPropertyPathConfiguration:osdu:wks:master-data--Wellbore:1`.
  Code, Name and Descriptions are filled with meaningful data as usual for all reference-data types.
  * The value of `data.Code` in the record refers to a major schema version kind which must be ended with version major and dot. For example, `"osdu:wks:master-data--Well:1."`.
  * The additional index properties are added with one JSON object each in the `Configurations[]` array. Each `Configurations[]` element has:
    * The `Name` defined the name of the index 'column', or the name of the property one can search for. 
    * The `Policy` decides, in the current usage, whether the resulting value is a single value or an array containing the aggregated, derived values. It can be either `ExtractFirstMatch` or `ExtractAllMatches`.
    * At least one element defined in `Paths[]`. Each `Path` in `Paths[]` includes the mandatory object `ValueExtraction` and the optional object `RelatedObjectsSpec`
      * `ValueExtraction`
        * It has one mandatory property, `ValuePath` which defines path of the source value. 
        * It has two optional properties, `RelatedConditionProperty` and `RelatedConditionMatches`. The value selection can be filtered by a match condition of these two properties.The `RelatedConditionMatches` can be a list of strings or regular expressions.
      * `RelatedObjectsSpec`: 
        * If no `RelatedObjectsSpec` is present, the value is derived from the object being indexed. 
        * If `RelatedObjectsSpec` is provided, the value extraction is carried out in related objects. 
          * `RelationshipDirection` is one of the mandatory property. It defines the relationship of the current object to the related object. It can be either `ChildToParent` or `ParentToChildren`
          * `RelatedObjectKind` and `RelatedObjectID` are other two mandatory prperites to define the kind of the related object(s) and id as reference key for the related objects. 
          * `RelatedConditionProperty` and `RelatedConditionMatches` are optional properties. The related object selection can be filtered by a match condition of these two properties. The `RelatedConditionMatches` can be a list of strings or regular expressions.

With this, the extension properties can be defined as if they were provided by a schema.

Most of the use cases deal with text (string) types. The definition of configurations is however not limited to string
types. As long as the property is known to the indexer, i.e., the source record schema is describing the types, the type
can be inferred by the indexer. This does not work for nested arrays of objects, which have not been indexed
with `"x-osdu-indexing": {"type":"nested"}`. In this case the types unknown to the Indexer Service are
string-serialized; the resulting index type is then of type `string` if the `Policy` is `ExtractFirstMatch` or `string` 
array if the `Policy` is `ExtractAllMatches`, still supporting text search.

For more information about the index augmenter, please check with the [ADR #81](https://community.opengroup.org/osdu/platform/system/indexer-service/-/issues/81) 

In [ADR #281](https://community.opengroup.org/osdu/platform/system/indexer-service/-/issues/281). we would like to extend the current Index Augmenter 
with function framework. It will allow developers to add functions that can process data from one or more source properties to generate aggregated results 
to further support search and data preview. Here are a few highlights:
- Each augmenter function self-interprets the `ValuePath` property under `ValueExtraction` which means it can accept one or more source property values and extra-metadata, e.g. unit.
- Each augmenter function must implement IAugmenterFunction and register to AugmenterFunctionFactory. 

## Use Cases
###Use Case 1: WellUWI

_As a user I want to discover and match Wells by their UWI. I am aware that this is not globally reliable, however, I am
able to specify a prioritized AliasNameType list to look up value in the NameAliases array._

The configuration demonstrates extractions from the record being indexed itself. With Policy `ExtractFirstMatch`, the
first value matching the condition `RelatedConditionProperty` is equal to one of `RelatedConditionMatches`.

<details><summary>Configuration for Well, extract WellUWI from NameAliases[]</summary>

```json
{
  "data": {
    "Code": "osdu:wks:master-data--Well:1.",
    "Configurations": [
      {
        "Name": "WellUWI",
        "Policy": "ExtractFirstMatch",
        "Paths": [
          {
            "ValueExtraction": {
              "RelatedConditionMatches": [
                "^[\\w\\-\\.]+:reference-data--AliasNameType:UniqueIdentifier:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:RegulatoryName:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:PreferredName:$",
                "^[\\w\\-\\.]+:reference-data--AliasNameType:CommonName:$"
              ],
              "RelatedConditionProperty": "data.NameAliases[].AliasNameTypeID",
              "ValuePath": "NameAliases[].AliasName"
            }
          }
        ],
        "UseCase": "As a user I want to discover and match Wells by their UWI. I am aware that this is not globally reliable, however, I am able to specify a prioritized AliasNameType list to look up value in the NameAliases array."
      }
    ]
  }
}
```

</details>

---

### Use Case 2: CountryNames

_As a user I want to find objects by a country name, with the understanding that an object may extend over country
boundaries._

This configuration demonstrates the extraction from related index objects - here `RelatedObjectKind`
being `osdu:wks:master-data--GeoPoliticalEntity:1.`, which are found via `RelatedObjectID` as
in `data.GeoContexts[].GeoPoliticalEntityID`. The condition is constrained to be that GeoTypeID is
GeoPoliticalEntityType:Country.

<details><summary>Configuration for Well, extract CountryNames from GeoContexts[]</summary>

```json
{
  "data": {
    "Code": "osdu:wks:master-data--Well:1.",
    "Configurations": [
      {
        "Name": "CountryNames",
        "Policy": "ExtractAllMatches",
        "Paths": [
          {
            "RelatedObjectsSpec": {
			  "RelationshipDirection": "ChildToParent",
              "RelatedObjectID": "data.GeoContexts[].GeoPoliticalEntityID",
              "RelatedObjectKind": "osdu:wks:master-data--GeoPoliticalEntity:1.",
              "RelatedConditionMatches": [
                "^[\\w\\-\\.]+:reference-data--GeoPoliticalEntityType:Country:$"
              ],
              "RelatedConditionProperty": "data.GeoContexts[].GeoTypeID"
            },
            "ValueExtraction": {
              "ValuePath": "GeoPoliticalEntityName"
            }
          }
        ],
        "UseCase": "As a user I want to find objects by a country name, with the understanding that an object may extend over country boundaries."
      }
    ]
  }
}
```

</details>

---

### Use Case 3: Wellbore Name on WellLog Children

_As a user I want to discover WellLog instances by the wellbore's name value._

A variant of this can be WellUWI from parent Wellbore &rarr; Well; in that case the value would be derived from the
already extended index values.

This configuration demonstrates extractions from multiple `Paths[]`.

<details><summary>Configuration for WellLog, extract WellboreName from parent WellboreID</summary>

```json
{
  "data": {
    "Code": "osdu:wks:work-product-component--WellLog:1.",
    "Configurations": [
      {
        "Name": "WellboreName",
        "Policy": "ExtractFirstMatch",
        "Paths": [
          {
            "RelatedObjectsSpec": {
			  "RelationshipDirection": "ChildToParent",
              "RelatedObjectKind": "osdu:wks:master-data--Wellbore:1.",
              "RelatedObjectID": "data.WellboreID"
            },
            "ValueExtraction": {
              "ValuePath": "VirtualProperties.DefaultName"
            }
          },
          {
            "RelatedObjectsSpec": {
			  "RelationshipDirection": "ChildToParent",
              "RelatedObjectKind": "osdu:wks:master-data--Wellbore:1.",
              "RelatedObjectID": "data.WellboreID"
            },
            "ValueExtraction": {
              "ValuePath": "FacilityName"
            }
          }
        ],
        "UseCase": "As a user I want to discover WellLog instances by the wellbore's name value."
      }
    ]
  }
}
```

</details>

---

### Use Case 4: Wellbore index WellLogCurveMnemonics

_As a user I want to find Wellbores by well log mnemonics._

This configuration demonstrates the Policy `ExtractAllMatches` with related objects discovered by
RelationshipDirection `ParentToChildren`, i.e., related objects referring the indexed record.

<details><summary>Configuration for WellLog, extract WellboreName from parent WellboreID</summary>

```json
{
  "data": {
    "Code": "osdu:wks:master-data--Wellbore:1.",
    "Configurations": [
      {
        "Name": "WellLogCurveMnemonics",
        "Policy": "ExtractAllMatches",
        "Paths": [
          {
            "RelatedObjectsSpec": {
              "RelationshipDirection": "ParentToChildren",
              "RelatedObjectID": "data.WellboreID",
              "RelatedObjectKind": "osdu:wks:work-product-component--WellLog:1."
            },
            "ValueExtraction": {
              "ValuePath": "Curves[].Mnemonic"
            }
          }
        ],
        "UseCase": "As a user I want to find Wellbores by well log mnemonics."
      }
    ]
  }
}
```

</details>

---

### Use Case 5: Entity Names on the Document

_When a document is ingested, it can associate with one or more parent entities. As a user I want to discover
all the related instances, including the documents, by the entity's name value._

This configuration demonstrates how to extend properties from parent entities into document record when the kind(s) of
the parent entities are not well-defined in the document schema.

<details><summary>Configuration for Document, extract Name from parent entities Wellbore and SeismicAcquisitionSurvey</summary>

```json
{
  "data": {
    "Code": "osdu:wks:work-product-component--Document:1.",
    "Configurations": [{
        "Name": "AssociatedFacilityNames",
        "Policy": "ExtractAllMatches",
        "Paths": [{
          "RelatedObjectsSpec": {
            "RelationshipDirection": "ChildToParent",
            "RelatedObjectID": "data.LineageAssertions[].ID",
            "RelatedObjectKind": "osdu:wks:master-data--Wellbore:1.",
            "RelatedConditionMatches": [
              "^[\\w\\-\\.]+:master-data\\-\\-Wellbore:[\\w\\-\\.\\:\\%]+$"
            ],
            "RelatedConditionProperty": "data.LineageAssertions[].ID"
          },
          "ValueExtraction": {
            "ValuePath": "FacilityName"
          }
        }]
      }, {
        "Name": "AssociatedProjectNames",
        "Policy": "ExtractAllMatches",
        "Paths": [{
          "RelatedObjectsSpec": {
            "RelationshipDirection": "ChildToParent",
            "RelatedObjectID": "data.LineageAssertions[].ID",
            "RelatedObjectKind": "osdu:wks:master-data--SeismicAcquisitionSurvey:1.",
            "RelatedConditionMatches": [
              "^[\\w\\-\\.]+:master-data\\-\\-SeismicAcquisitionSurvey:[\\w\\-\\.\\:\\%]+$"
            ],
            "RelatedConditionProperty": "data.LineageAssertions[].ID"
          },
          "ValueExtraction": {
            "ValuePath": "ProjectName"
          }
        }]
      }
    ]
  }
}
```

</details>

### Use Case 6: Augmenter Function ```Extent``` to compute the extent of a shape in geodetic coordinate WGS84

This configuration demonstrates the Augmenter function `Extent` to compute the extent of geodectic shape from `VirtualProperties.DefaultLocation.Wgs84Coordinates`.

<details><summary>Configuration for SeismicTraceData, compute the extent from Wgs84Coordinates</summary>

```json
{
    "data": {
        "Code": "osdu:wks:work-product-component--SeismicTraceData:1.",
        "Configurations": [{
                "Name": "Extent",
                "Policy": "ExtractFirstMatch",
                "Paths": [{
                        "ValueExtraction": {
                            "ValuePath": "Extent(data.VirtualProperties.DefaultLocation.Wgs84Coordinates)"
                        }
                    }
                ],
                "UseCase": "As a user I want to know the extent of a geodectic shape and use it to filter (search) data."
            }
        ]
    }
}

```

</details>

---

### Use Case 7: Augmenter Function ```Len``` to compute the (total) length of a polyline or multi-polyline shape in geodetic coordinate WGS84

This configuration demonstrates the Augmenter function `Len` to compute the (total) length in meter of a polyline or multi-polyline shape from `VirtualProperties.DefaultLocation.Wgs84Coordinates`.

<details><summary>Configuration for SeismicLineGeometry, compute the length from Wgs84Coordinates</summary>

```json
{
    "data": {
        "Code": "osdu:wks:work-product-component--SeismicLineGeometry:1.",
        "Configurations": [{
                "Name": "Length",
                "Policy": "ExtractFirstMatch",
                "Paths": [{
                        "ValueExtraction": {
                            "ValuePath": "Len(data.VirtualProperties.DefaultLocation.Wgs84Coordinates)"
                        }
                    }
                ],
                "UseCase": "As a user I want to know the length of a polyline or multi-polyline shape and use it to filter (search) data."
            }
        ]
    }
}

```

</details>

---

### Use Case 8: Augmenter Function ```Area``` to compute the (total) length of a polygon or multi-polygon shape in geodetic coordinate WGS84

This configuration demonstrates the Augmenter function `Area` to compute the (total) area in square meter of a polygon or multi-polygon shape from `VirtualProperties.DefaultLocation.Wgs84Coordinates`.

<details><summary>Configuration for SeismicBinGrid, compute the extent from `VirtualProperties.DefaultLocation.Wgs84Coordinates`</summary>

```json
{
    "data": {
        "Code": "osdu:wks:work-product-component--SeismicBinGrid:1.",
        "Configurations": [{
                "Name": "Area",
                "Policy": "ExtractFirstMatch",
                "Paths": [{
                        "ValueExtraction": {
                            "ValuePath": "Area(data.VirtualProperties.DefaultLocation.Wgs84Coordinates)"
                        }
                    }
                ],
                "UseCase": "As a user I want to know the area of a polygon or multi-polygon shape and use it to filter (search) data."
            }
        ]
    }
}

```

</details>

---

## Governance

OSDU Data Definition ships reference value list content for all reference-data group-type entities. The type
IndexPropertyPathConfiguration is classified as OPEN governance, which usually means that new records can be added by
platform operators. This rule must be adjusted for IndexPropertyPathConfiguration records.

### Permitted Changes to IndexPropertyPathConfiguration Records

It is permitted to

* customize the conditions for value extractions, notable the matching values in `RelatedConditionMatches`.
* add additional `Paths[]` elements to `Configurations[].Paths[]`
* add new index property configuration objects to the `Configurations[]` array. To avoid interference with future OSDU
  updates it is strongly recommended to add a namespace prefix to the Configurations[].Name, e.g., "OperatorX.WellUWI".

### Prohibited Changes to IndexPropertyPathConfiguration Records

It is not permitted to

* change the target value type of existing, OSDU shipped index extensions. Example the `ExtractionPath` to a string
  property in the original OSDU `Configurations[].ValueExtraction.ValuePath` must not be altered to a number, integer,
  or array.
* change the meaning of existing, OSDU shipped index extensions.
* remove OSDU shipped extension definitions in Configurations[].

## Accepted Limitations

* To make the change of the IndexPropertyPathConfiguration to take effect, 
  * Before `M23`, it requires re-indexing of all the records of a major schema version kind.
  * From `M23`, it does not require re-indexing for new records or updated records. To update all the existing records, it still requries re-indexing.
  * For re-indexing, you don't need to use 'force_clean' option anymore. Users can still search the data during the re-index process. Please be aware that the search result could mix the non-updated and updated records before the re-index is fully completed.

* One IndexPropertyPathConfiguration record corresponds to one schema kind's major version. Given the deployment of the 
  IndexPropertyPathConfiguration record is via the [Storage Service](https://osdu.pages.opengroup.org/platform/system/storage/) API, it can't prevent users from deploying multiple records
  for one schema kind. `Indexer augmenter` engine does not merge the multiple records to one and only picks one randomly before M19. 
  After M20, the last modified record will be picked by the engine.

* To prevent more than one IndexPropertyPathConfiguration record corresponds to one schema kind's major version, all 
  IndexPropertyPathConfiguration records should have ids defined with the naming pattern described in the [Introduction](#introduction).

* All the extensions defined in the IndexPropertyPathConfiguration records refer to properties in the `data` block,
  including `Name`, `ValuePath`, `RelatedObjectID`, `RelatedConditionProperty`. System properties are
  out of reach.
   * The prefix `data.` in `Name` and `ValuePath` are therefore optional and can be omitted.
   * The prefix `data.` in  `RelatedObjectID` and `RelatedConditionProperty` are required.

* The formats/values of the extended properties are extracted from the formats/values of the related index records. If
  the formats of the original properties are unknown in the related index records, the indexer will set the value type
  of the extended properties as string or string array. (With additional complexity and schema parsing, this limitation
  can be overcome, but currently the added value seems to be marginal.)

* If the extended properties are extracted from arrays of objects indexed with
  (`"x-osdu-indexing": {"type":"flattened"}`), the indexer cannot re-construct the object properties to the
  nested objects when the policy `ExtractAllMatches` is applied. (The kind of indexing is already a deliberate choice.
  With additional complexity, this limitation can be overcome, but currently the added value seems to
  be marginal.)

* To simplify the solution, all the related kinds defined in the configuration are kinds with major version only. They
  must end with dot ".". For example: `"RelatedObjectKind": "osdu:wks:work-product-component--WellLog:1."`.

* Index updates may take time. Immediate consistency cannot be expected.

* When a kind derives extended properties from its parent(s), a new data property `data.AssociatedIdentities` is added
  on demand by the indexer. The property name `AssociatedIdentities` is therefore reserved by the Indexer and shall not
  be used in any OSDU schemas.
  Currently, the property name `AssociatedIdentities` is not in use in any of the OSDU well-known schemas. Tests will be
  implemented in the OSDU Data Definition pipeline to ensure that this reserved name does not appear as property in
  the `data` block.

* When the policy `ExtractAllMatches` is applied, the `Index Augmenter` will aggregate the `unique` values from all matched sources
  to an array. The aggregated array could be large, especially when the relationship is `ParentToChildren`. There is a limitation
  on the size of the array that ElasticSearch processes. A record will fail to be indexed if its property with array type
  has more than 10,000 items. In order to balance the performance, the array size of the extended property should not be too
  large. From M25, if the array size of the extended property exceeds the pre-defined limit, the extended property will be removed
  and a message will be logged in the index of the record. The pre-defined limit is 2,000 by default. It can be
  overridden by the setting in the Application.properties, e.g. `augmenter.extended_list_value.max_size=5000`.


## Deployment

Like the reference data, the deployment and un-deployment of the IndexPropertyPathConfiguration records can be through [Storage Service](https://osdu.pages.opengroup.org/platform/system/storage/) API

## Troubleshooting

After an IndexPropertyPathConfiguration record to a major schema version kind is created or updated and 
all the records of the major schema version kind have been re-indexed. If the extended properties fail to be created in all 
the records from the `OSDU search` results, any one of the following mistakes can contribute to the failure:

* The feature flag `index-augmenter-enabled` for `Index Augmenter` is not enabled in the given data partition. Please check 
  with the service provider.

* Any one of the mandatory properties is missing, such as `data.Code`, `data.Configurations[].Name`, `data.Configurations[].Policy` 
  or `data.Configurations[].Paths[].ValueExtraction.ValuePath` and etc. 

* The value of `data.Code` in the record is not a major schema version kind which is ended with version major and dot.

* Multiple IndexPropertyPathConfiguration records to a major schema version kind may exist. Using kind 
  `osdu:wks:work-product-component--WellLog:1.` as example to run OSDU query:
```
  { 
     "kind": "osdu:wks:reference-data--IndexPropertyPathConfiguration:1.0.0",
     "query": "data.Code: \"osdu:wks:work-product-component--WellLog:1.\""
  }
```

* If not all extended properties are missing, the `Configurations[]` of the missing extended properties could be invalid. 
  The `Index Augmenter` engine can do basic syntax check on each configuration and only ignore the invalid ones.
