Schema Service schema conversion.
=================================

Purpose
-------

The purpose of this document is to describe schema conversion from the
Schema Service formats to the Storage Service format.

Storage Service schema has the following JSON format
----------------------------------------------------
```json
{
  "kind": "<kind>",
  "schema": [
    {
      "kind": "<type>",
      "path": "<path>"
    },
    {
      "kind": "<type>",
      "path": "<path>"
    },
	…
}

```

Where \<kind\> - id of a kind, \<type\> - type of the described entity
(for instance link, string,datetime, \[\]string, etc.), \<path\> -
path/name/id of the described entity (for instance FacilityID, WellID,
ProjectedBottomHoleLocation.CoordinateQualityCheckDateTime, etc.)

Shema Service format follows JSON schema format. 
------------------------------------------------

Please see <https://tools.ietf.org/html/draft-handrews-json-schema-02>
for the details

Example

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Wellbore",
  "description": "A hole in the ground extending from a point at the earth's surface to the maximum point of penetration.",
  "type": "object",
  "properties": {…},
  "required": […],
  "additionalProperties": false,
  "definitions": {…}
}

```

We are interested in "properties.data" and "definitions" sections.

"definitions" contains definitions of complex types. "properties.data"
contains attributes that are used in a certain schema and can refer to
the definition section (in that case we have to unwrap definition
section and turn into simple types). Sibling properties to
properties.data are ignored as the so-called system properties are
shared with all records.

"properties.data" may have 0..n references to the "definitions" section
and 0..m properties that describe schema sections. For instance

```json
"data": {
  "$ref": "#/definitions/wellboreData",
  "description": "Wellbore data container",
  "title": "Wellbore Data"
}

```

This means "wellboreData" has to be found among definitions and data are
included

```json
"data": {
  "allOf": [
    {
      "$ref": "#/definitions/opendes:wks:AbstractFacility.1.0.0"
    },
    {
      "type": "object",
      "properties": {
        "WellID": {
          "type": "string",
          "pattern": "^srn:<namespace>:master-data\\/Well:[^:]+:[0-9]*$"
        },
        "SequenceNumber": {
          "description": "A number that indicates the order in which wellbores were drilled.",
          "type": "integer"
        },
…

```


This means \"AbstractFacility.1.0.0\" must be processed, plus
\"WellID\", \"SequenceNumber\", ...

References can have references to other reference(-s), in that case
Storage Schema has a composite path.

For instance,

```json
"elevationReference": {
  "$ref": "#/definitions/opendes:wks:simpleElevationReference:1.0.0",
  "description": "…",
  "title": "Elevation Reference",
  "type": "object"
},
…
"simpleElevationReference": {
  "description": "...",
  "properties": {
    "elevationFromMsl": {
      "$ref": "#/definitions/opendes:wks:valueWithUnit:1.0.0",
      "description": "…",
      "example": 123.45,
      "title": "Elevation from MSL",
      "x-slb-measurement": "Standard_Depth_Index"
    },
…
"valueWithUnit": {
  "description": "Number value ...",
  "properties": {
    "value": {
      "description": "Value of the corresponding...",
      "example": 30.2,
      "title": "Value",
      "type": "number"
    }
  }

```

Is converted to

```json
{
  "kind": "double",
  "path": "elevationReference.elevationFromMsl.value"
}

```

\"path\":\"elevationReference.elevationFromMsl.value\" consists of 3
names separated with dot.

Not all data are converted to the storage service schema format:
----------------------------------------------------------------

1.  Definitions

Definitions must follow the pattern a:b:name:version, where a,b,name are required and version is optional.
For instance

```json
opendes:wks:AbstractAnyCrsFeatureCollection:1.0.0
opendes:wks:anyJsonFeatureCollection:1.0.0
```

Ignored definition(-s) name(-s) are not included into Storage Service schema:

```json
AbstractAnyCrsFeatureCollection
anyCrsGeoJsonFeatureCollection
```

Following definitions are not unwrapped and kind is determined according
to the following types conversions:

```json
AbstractFeatureCollection:version -> core:dl:geoshape:version
geoJsonFeatureCollection -> core:dl:geoshape:1.0.0
core_dl_geopoint -> core:dl:geopoint:1.0.0
```

for instance

```json
"Wgs84Coordinates": {
  "title": "WGS 84 Coordinates",
  "description": "…",
  "$ref": "#/definitions/opendes:wks:AbstractFeatureCollection.1.0.0"
}

```

Is converted to

```json
{
  "kind": "core:dl:geoshape:1.0.0",
  "path": "Wgs84Coordinates"
}
```

2.  Arrays

Arrays of complex types by default will be consumed as object type
```json
"Markers": {
"type": "array",
    "items": {
        "type": "object", 
        "properties":  {
            "NegativeVerticalDelta"{
                "description": "The distance vertically below the Marker position that marks the limit of the high confidence range for the Marker pick.",
                "x-osdu-frame-of-reference": "UOM:length",
                "type": "number"
            },
          .....
        }
    }
}        

```
Without inner objects processing
```json
{
        path = Markers,
        kind = []object
}
```

Processing can be specified with optional "x-osdu-indexing" property 
```json
"properties": {
    "Markers": {
        "x-osdu-indexing": {
        "type": "nested"
         },
        "type": "array",
        "items": {
            "type": "object",
        "properties":  {
            "NegativeVerticalDelta"{
                "description": "The distance vertically below the Marker position that marks the limit of the high confidence range for the Marker pick.",
                "x-osdu-frame-of-reference": "UOM:length",
                "type": "number"
            },
          .....
```
"x-osdu-indexing" property values
```json
"nested" , "flattened"
```
By default, only "nested" type will lead to inner objects processing
```json
{ 
path = Markers,
        kind = nested,
        properties = [{
                path = NegativeVerticalDelta,
                kind = double
            },
        .....
}
```

Arrays of primitive types are supported

```json
"number", "string", "integer", "boolean"
```

Following primitive types are converted to the Storage Service Schema types (all other types like string are used as is):
----------------------------------------------------------------------------

```json
"date-time"->"datetime"
"date"->"datetime"
"int64"->"long"
"number"->"double"
"bool"->"boolean"
"integer"->"int"
```

Type selection according to attributes.
---------------------------------------

One or more attributes of a single entity may have values with types.
Following attributes are considered to select a type for the Storage
Schema kind (ordered according to selection priority):
```json
"pattern", "format", "items.type", "type"
```
If \"pattern\" starts with \"\^srn\" the returned kind is \"link\"

Arrays of primitive types have \[\] before the type (for instance
\"\[\]string\")

Examples
--------

#### Simple String

```json
"FacilityID": {
  "description": "A system-specified unique identifier of a Facility.",
  "type": "string"
},

```

\"kind\":\"string\"

#### Nested Arrays of Structures

```json
"FacilityTypeID": {
  "description": "The definition of a kind of capability to perform a business function or a service.",
  "type": "string",
  "pattern": "^srn:<namespace>:reference-data\\/FacilityType:[^:]+:[0-9]*$"
},

```

\"kind\":\"link\"

```json
"FacilityOperator": {
  "description": "The history of operator organizations of the facility.",
  "type": "array",
  "items": {
    "$ref": "#/definitions/opendes:wks:AbstractFacilityOperator.1.0.0"
  }
}

```

Ignored for now (array of references)

#### Object References by ID

```json
"externalIds": {
  "description": "An array of identities (e.g. some kind if URL to be resolved in an external data store), which links to external realizations of the same entity.",
  "format": "link",
  "items": {
    "type": "string"
  },
  "title": "Array of External IDs",
  "type": "array"
},

```

\"kind\": \"\[\]link\"

#### Long Integers

```json
"version": {
  "description": "The version number of this wellbore; set by the framework.",
  "example": "1040815391631285",
  "format": "int64",
  "title": "Entity Version Number",
  "type": "number"
}

```

\"kind\": \"long\"

Processing specifics
----------------------------------------------------------------------------

allOf, anyOf and oneOf tags are processed at the same way. All internal data(properties) are included into converted schema.

For instance
```json
{
  "definitions": {
    "wellboreData1": {
      "properties": {
        "prop1": {
          "type": "string"
        }
      }
    },
    "wellboreData2": {
      "properties": {
        "prop2": {
          "type": "string"
        }
      }
    }
  },
  "properties": {
    "data": {
      "allOf": [
        {
          "anyOf": [
            {
              "$ref": "#/definitions/wellboreData1"
            } ],
          "oneOf": [
            {
              "$ref": "#/definitions/wellboreData2"
            }
          ]
        }
      ]
    }
  }
}

```

is converted to

```json
{
  "kind": "KIND_VAL",
  "schema": [
    {
      "kind": "string",
      "path": "prop1"
    },
    {
      "kind": "string",
      "path": "prop2"
    }
    ]
}
```