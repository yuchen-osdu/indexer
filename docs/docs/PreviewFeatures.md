# Preview Features
## Geoshape Decimation

In order to improve indexing and search performance for documents with large geometry, the geo-shape of the following
GeoJSON types in the original shape attribute and virtual shape attribute if exists are decimated
by implementing Ramer–Douglas–Peucker algorithm:
- LineString
- MultiLineString
- Polygon
- MultiPolygon

The feature is enabled for all data partitions since M19.

## Index extension

OSDU Standard index extensions are defined by OSDU Data Definition work-streams with the intent to provide
user/application friendly, derived properties. The standard set, together with the OSDU schemas, form the
interoperability foundation. They can contribute to deliver domain specific APIs according to the Domain Driven Design
principles.

The configurations are encoded in OSDU reference-data records, one per each major schema version. The type name
is IndexPropertyPathConfiguration. With this, the extension properties can be defined as if they were provided by a schema.

The augmentation feature can be controlled by a feature flag that is managed via
the `Partition Service`. The feature is `ON` by default since `M25`. The service provider can explicitly turn it off   
if the augmentation feature is not desirable for its clients. 

Here is an example to turn off augmentation feature by setting the property 
"index-augmenter-enabled" in a given data partition:
```
{
   "index-augmenter-enabled": {
        "sensitive": false,
        "value": "false"
    }
}
```

When the property "index-augmenter-enabled" is set to "false" (String type) via `Partition Service` for the
given data partition, the configurations defined as type IndexPropertyPathConfiguration will be ignored and index extension will be disabled. 

## Search text with special characters '_' and '.'

OSDU indexer and search use Elasticsearch default analyzer (or called standard analyzer) to analyzes the unstructured 
text when they are indexed and searched. Due to the way Elasticsearch standard analzyer analyzes unstructured text, 
it is very difficult if not impossible to perform certain high-value searches on unstructured content. For example, 
users want to search for a file with file name `1-ABC_Seismic_Report.pdf`, it is impossible to use one or two keywords 
in the file name like "abc", "seismic", "report" to search the file or pdf extension to find search all pdf files. 
User can't even use wildcard like `*seismic*` to search the file as wildcard in prefix is not supported. The user would 
have to search using exact match or at a minimum ABC_Seismic* if they want to use wildcards.

In the [ADR](doc:https://community.opengroup.org/osdu/platform/system/indexer-service/-/issues/186), we propose a 
change to extend the Elasticsearch Standard Analyzer to process two additional special characters as word delimiter:
- underscore `_`
- dot `.`. It will be handled like character `,`. Please note that Elasticsearch Standard Analyzer does not take the `,` 
  as word delimiter if it is part of number string, e.g. `1,663m`. In this proposal, the `.` will be processed in the 
  similar way, e.g. `-999.25` or `10.88` in which `.` won't be treated as word delimiter.

In order to reduce risks (e.g. work interruption) on re-indexing, we will manage this solution with a feature flag that 
is set by the Partition Service. Here is an example to enable this feature by setting the property
"custom-index-analyzer-enabled" in a given data partition:
```
{
   "custom-index-analyzer-enabled": {
        "sensitive": false,
        "value": "true"
    }
}
``` 

If the property "custom-index-analyzer-enabled" is not created or the property value is set to "false" (String type) in the
given data partition, the default index analyzer will be applied to indexing and search.

After the feature is enabled, **it will require re-indexing kinds in the given partition in oder to adopt the custom analyzer.**
 

## Index AsIngestedCoordinates

Source: [issue 95](https://community.opengroup.org/osdu/platform/system/indexer-service/-/issues/95)

When ingesting a record such as this Well with AsIngestedCoordinates:
```
"data": {
  // AbstractSpatialLocation
  "SpatialLocation": {
    // AbstractAnyCrsFeatureCollection
    "AsIngestedCoordinates": {
      "CoordinateReferenceSystemID": "osdu:reference-data--CoordinateReferenceSystem:BoundProjected:EPSG::32021_EPSG::15851:",
      "VerticalCoordinateReferenceSystemID": "osdu:reference-data--CoordinateReferenceSystem:Vertical:EPSG::5714:",
      "VerticalUnitID": "osdu:reference-data--UnitOfMeasure:m:",
      "persistableReferenceCrs": "{\"authCode\":{\"auth\":\"OSDU\",\"code\":\"32021079\"},\"lateBoundCRS\":{\"authCode\":{\"auth\":\"EPSG\",\"code\":\"32021\"},\"name\":\"NAD_1927_StatePlane_North_Dakota_South_FIPS_3302\",\"type\":\"LBC\",\"ver\":\"PE_10_9_1\",\"wkt\":\"PROJCS[\\\"NAD_1927_StatePlane_North_Dakota_South_FIPS_3302\\\",GEOGCS[\\\"GCS_North_American_1927\\\",DATUM[\\\"D_North_American_1927\\\",SPHEROID[\\\"Clarke_1866\\\",6378206.4,294.9786982]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433]],PROJECTION[\\\"Lambert_Conformal_Conic\\\"],PARAMETER[\\\"False_Easting\\\",2000000.0],PARAMETER[\\\"False_Northing\\\",0.0],PARAMETER[\\\"Central_Meridian\\\",-100.5],PARAMETER[\\\"Standard_Parallel_1\\\",46.18333333333333],PARAMETER[\\\"Standard_Parallel_2\\\",47.48333333333333],PARAMETER[\\\"Latitude_Of_Origin\\\",45.66666666666666],UNIT[\\\"Foot_US\\\",0.3048006096012192],AUTHORITY[\\\"EPSG\\\",32021]]\"},\"name\":\"NAD27 * OGP-Usa Conus / North Dakota CS27 South zone [32021,15851]\",\"singleCT\":{\"authCode\":{\"auth\":\"EPSG\",\"code\":\"15851\"},\"name\":\"NAD_1927_To_WGS_1984_79_CONUS\",\"type\":\"ST\",\"ver\":\"PE_10_9_1\",\"wkt\":\"GEOGTRAN[\\\"NAD_1927_To_WGS_1984_79_CONUS\\\",GEOGCS[\\\"GCS_North_American_1927\\\",DATUM[\\\"D_North_American_1927\\\",SPHEROID[\\\"Clarke_1866\\\",6378206.4,294.9786982]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433]],GEOGCS[\\\"GCS_WGS_1984\\\",DATUM[\\\"D_WGS_1984\\\",SPHEROID[\\\"WGS_1984\\\",6378137.0,298.257223563]],PRIMEM[\\\"Greenwich\\\",0.0],UNIT[\\\"Degree\\\",0.0174532925199433]],METHOD[\\\"NADCON\\\"],PARAMETER[\\\"Dataset_conus\\\",0.0],OPERATIONACCURACY[5.0],AUTHORITY[\\\"EPSG\\\",15851]]\"},\"type\":\"EBC\",\"ver\":\"PE_10_9_1\"}",
      "persistableReferenceVerticalCrs": "{\"authCode\":{\"auth\":\"EPSG\",\"code\":\"5714\"},\"name\":\"MSL_Height\",\"type\":\"LBC\",\"ver\":\"PE_10_9_1\",\"wkt\":\"VERTCS[\\\"MSL_Height\\\",VDATUM[\\\"Mean_Sea_Level\\\"],PARAMETER[\\\"Vertical_Shift\\\",0.0],PARAMETER[\\\"Direction\\\",1.0],UNIT[\\\"Meter\\\",1.0],AUTHORITY[\\\"EPSG\\\",5714]]\"}",
      "persistableReferenceUnitZ": "{\"scaleOffset\":{\"scale\":1.0,\"offset\":0.0},\"symbol\":\"m\",\"baseMeasurement\":{\"ancestry\":\"Length\",\"type\":\"UM\"},\"type\":\"USO\"}",
      "features": [  // NOTE: A well will only have a single AnyCrsPoint for the surface location, potentially 2D, rather than 3D (and then also no vertical CRS, etc.).  But I added here the 3D and additional AnyCrsLineString just to make clear what to do in this case.
        {
          "type": "AnyCrsFeature"
          "geometry": {
            "type": "AnyCrsPoint"
            "coordinates": [1500000.0, 12345678.0, 100.0] 
          }
        },
        {
          "type": "AnyCrsFeature"
          "geometry": {
            "type": "AnyCrsLineString"
            "coordinates": [[1400000.0, 12345666.0, 99.0], [1600000.0, 12345777.0, 101.0]]
          }
        }
      ]    
      // Wgs84 Coordinates
      "Wgs84Coordinates": { etc. Not relevant}
    },
    "SpatialLocationCoordinatesDate":    "2023-02-19",
    "QuantitativeAccuracyBandID":        "<1 m",
    "QualitativeSpatialAccuracyTypeID":  "Checked: Approved",
    "CoordinateQualityCheckPerformedBy": "Bert",
    "CoordinateQualityCheckDateTime":    "2023-01-19",
    "CoordinateQualityCheckRemarks": [
      "good",
      "really",
      "vertical is good too"
    ],
    "AppliedOperations": [
      "conversion from ED_1950_UTM_Zone_31N to GCS_European_1950; 1 points converted",
      "transformation GCS_European_1950 to GCS_WGS_1984 using ED_1950_To_WGS_1984_24; 1 points successfully transformed"
    ],
    "SpatialParameterTypeID": "Outline",
    "SpatialGeometryTypeID": "Point"
  },
}
```

The desired end result of a query search response would include the following properties. They are a direct copy
of the input record AbstractSpatialLocation fragment. The search query can use greater than and less than on the point
coordinates as in:.
```
  "query":
    "data.SpatialLocation.AsingestedCoordinates.CoordinateReferenceSystemID:someCRSrefID AND"
    "data.SpatialLocation.AsingestedCoordinates.FirstPoint.X:(>200000 AND <510000) AND " 
    "data.SpatialLocation.AsingestedCoordinates.FirstPoint.Y:(>100000.0 AND <3110000.0 )"
    
```

Result:
```
{
  "data": {
    "SpatialLocation.AsingestedCoordinates.FirstPoint.X": 222222.0,                // Number (floating point) if given on ingest of course
    "SpatialLocation.AsingestedCoordinates.FirstPoint.Y": 111111.0,                // Number.
    "SpatialLocation.AsingestedCoordinates.FirstPoint.Z": 100.0,                   // Number.  Blank (null) unless the input had a Z value

    "SpatialLocation.AsingestedCoordinates.CoordinateReferenceSystemID":         "xxx",       // see note below. OSDU allows data ingesting with PR and not with a reference to a CRS record id.  What to do then?
    "SpatialLocation.AsingestedCoordinates.VerticalCoordinateReferenceSystemID": "xxx",       // for 3D Z value if in input    
    "SpatialLocation.AsingestedCoordinates.persistableReferenceCrs":           "string xxx", // see note below.
    "SpatialLocation.AsingestedCoordinates.persistableReferenceVerticalCrs":   "string xxx",
    "SpatialLocation.AsingestedCoordinates.persistableReferenceUnitZ":         "string xxx",

    "SpatialLocation.AsingestedCoordinates.QuantitativeAccuracyBandID":        "xxx",
    "SpatialLocation.AsingestedCoordinates.QualitativeSpatialAccuracyTypeID":  "xxx",
    "SpatialLocation.AsingestedCoordinates.CoordinateQualityCheckPerformedBy": "xxx",
    "SpatialLocation.AsingestedCoordinates.CoordinateQualityCheckDateTime":    "xxx",
    "SpatialLocation.AsingestedCoordinates.CoordinateQualityCheckRemarks":   ["(string array)"],
    "SpatialLocation.AsingestedCoordinates.AppliedOperations":               ["(string array)"]
  }
}
```

Note:
- AsingestedCoordinates.FirstPoint.Type is not needed because Wgs84Coordinates will have the original type. 
- AsingestedCoordinates.SpatialLocationCoordinatesDate is not needed because QC time is already there and this is more for plate motion that seems not needed at the moment.

If the property "featureFlag.asIngestedCoordinates.enabled" is not created or the property value is set to "false" (String type) in the given data partition, 
the AsIngestedCoordinates field will not be indexed. 

## Case agnostic strict search and sort
Currently for all strict searches we provide .keyword subfield. In this preview we are adding possibility to case agnostic
strict search with additional .keywordLower subfield. This allows simpler querying values which casing might be non-trivial.
Details how to construct search query to use consume this feature are described in search service tutorial.
Also it allows to sort text values truly alphabetically as currently uppercase words are before lowercase letters.

Feature is enabled for OSDU Data Platform deployment with keywordLower flag enabled in application properties file.

## Index bag of words and support for autocomplete
All non-flattened text fields are now copied to internal bagOfWords field.
This allows searching through nested fields using top level text query.
Also bagOfWords.autocomplete subfield with completion type was added, allowing Search application to implement autocomplete using this field. 

## Mapping of boolean values (string or boolean)

In OSDU releases prior to M22 boolean attributes were indexed and returned in search queries as boolean - for example:

	Well:1.1.0:  "WasBusinessInterestFinancialOperated": true

Then in M22, M23, and M24 boolean values in indexing and search were changed to strings. So a query for the same Well would return:

	Well:1.1.0, "WasBusinessInterestFinancialOperated": "true"

It turned out that adding autocast of a value to the index type caused this since the index type for boolean values were incorrectly set to string, but it didn't affect the actual data before. Other optional features (bag of words, autocomplete, highlighting) required this autocast.

In M25 we know that many users want to have their boolean data being back to boolean in search but because it requires re-indexing, we will give users the choice.

The two choices to select based on feature flag settings are:

1. For people who cannot perform migration (i.e. do not wish to re-index)
- Set feature flag mapBooleanToString false, as it is by default (we acknowledge this might seem backward)
- Behavior remains the same as M22-M24
- Index types for boolean data are still string
- Users will continue to see boolean as string

2. For new instances with no data and users that want to have data matching index type for possible future feature developments
- feature flag mapBooleanToString On (we acknowledge this might seem backward)
- Autocast of values is enabled
- Index types created for boolean data field are also boolean
- Users will see boolean as boolean, because autocast will convert them correctly.
- Reindexing is needed if the feature flag is changed after data are loaded.

### Notes

1. The mapBooleanToString featue flag can also be set by data partition. The details
of that setting are beyond the scope of this document.

2. When re-indexing the data, you will want to use the "force-clean=true" option.

### FAQ

How do i know if the flags are on?

	Run a search query on a kind with boolean attributes. See if the value is boolean or string, for example, as above:
	Well:1.1.0:  "WasBusinessInterestFinancialOperated": true

How do i turn them on?

	The feature flags must be set prior to building the OSDU instance, therefore your build provider/CSP must make the changes unless the customer builds the code base themselves.
