# Indexer Service

## Introduction

The Indexer API provides a mechanism for indexing documents that contain structured or unstructured data. Documents and
indices are saved in a separate persistent store optimized for search operations. The indexer [API](api.md) can index any number of documents.

The indexer is indexes attributes defined in the schema. Schema can be created at the time of record ingestion in OSDU Data Platform
via [Schema Service](https://osdu.pages.opengroup.org/platform/system/schema-service/). The Indexer service also adds number of OSDU Data Platform meta attributes such as id, kind,
parent, acl, namespace, type, version, legaltags, index to each record at the time of indexing.

## Features

### Geoshape Decimation

In order to improve indexing and search performance for documents with large geometry, the geo-shape of the following
GeoJSON types in the original shape attribute and virtual shape attribute if exists are decimated
by implementing Ramer–Douglas–Peucker algorithm:

- LineString
- MultiLineString
- Polygon
- MultiPolygon

The feature is enabled for all data partitions since M19.