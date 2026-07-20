{
  "kind": "osdu:osdu:Wellbore:1.0.0",
  "schema": [
    {
      "kind": "string",
      "path": "FacilityID"
    },
    {
      "kind": "link",
      "path": "FacilityTypeID"
    },
    {
      "kind": "link",
      "path": "DataSourceOrganisationID"
    },
    {
      "kind": "link",
      "path": "OperatingEnvironmentID"
    },
    {
      "kind": "string",
      "path": "FacilityName"
    },
    {
      "kind": "link",
      "path": "WellID"
    },
    {
      "path" : "SequenceNumber",
      "kind" : "int"
    },
    {
      "kind": "link",
      "path": "KickOffWellbore"
    },
    {
      "kind": "link",
      "path": "TrajectoryTypeID"
    },
    {
      "kind": "link",
      "path": "DefinitiveTrajectoryID"
    },
    {
      "kind": "link",
      "path": "TargetFormation"
    },
    {
      "kind": "link",
      "path": "PrimaryMaterialID"
    },
    {
      "kind": "string",
      "path": "DefaultVerticalMeasurementID"
    },
    {
      "kind": "datetime",
      "path": "ProjectedBottomHoleLocation.SpatialLocationCoordinatesDate"
    },
    {
      "kind": "link",
      "path": "ProjectedBottomHoleLocation.QuantitativeAccuracyBandID"
    },
    {
      "kind": "link",
      "path": "ProjectedBottomHoleLocation.QualitativeSpatialAccuracyTypeID"
    },
    {
      "kind": "string",
      "path": "ProjectedBottomHoleLocation.CoordinateQualityCheckPerformedBy"
    },
    {
      "kind": "datetime",
      "path": "ProjectedBottomHoleLocation.CoordinateQualityCheckDateTime"
    },
    {
      "kind": "[]string",
      "path": "ProjectedBottomHoleLocation.CoordinateQualityCheckRemarks"
    },
    {
      "kind": "core:dl:geoshape:1.0.0",
      "path": "ProjectedBottomHoleLocation.Wgs84Coordinates"
    },
    {
      "kind": "[]string",
      "path": "ProjectedBottomHoleLocation.OperationsApplied"
    },
    {
      "kind": "link",
      "path": "ProjectedBottomHoleLocation.SpatialParameterTypeID"
    },
    {
      "kind": "link",
      "path": "ProjectedBottomHoleLocation.SpatialGeometryTypeID"
    },
    {
      "kind": "datetime",
      "path": "GeographicBottomHoleLocation.SpatialLocationCoordinatesDate"
    },
    {
      "kind": "link",
      "path": "GeographicBottomHoleLocation.QuantitativeAccuracyBandID"
    },
    {
      "kind": "link",
      "path": "GeographicBottomHoleLocation.QualitativeSpatialAccuracyTypeID"
    },
    {
      "kind": "string",
      "path": "GeographicBottomHoleLocation.CoordinateQualityCheckPerformedBy"
    },
    {
      "kind": "datetime",
      "path": "GeographicBottomHoleLocation.CoordinateQualityCheckDateTime"
    },
    {
      "kind": "[]string",
      "path": "GeographicBottomHoleLocation.CoordinateQualityCheckRemarks"
    },
    {
      "kind": "core:dl:geoshape:1.0.0",
      "path": "GeographicBottomHoleLocation.Wgs84Coordinates"
    },
    {
      "kind": "[]string",
      "path": "GeographicBottomHoleLocation.OperationsApplied"
    },
    {
      "kind": "link",
      "path": "GeographicBottomHoleLocation.SpatialParameterTypeID"
    },
    {
      "kind": "link",
      "path": "GeographicBottomHoleLocation.SpatialGeometryTypeID"
    },
    {
      "path": "VerticalMeasurements",
      "kind": "[]object"
    },
    {
      "path": "DrillingReason",
      "kind": "[]object"
    },
    {
      "path": "FacilityNameAlias",
      "kind": "[]object"
    },
    {
      "path": "FacilityState",
      "kind": "[]object"
    },
    {
      "path": "FacilityEvent",
      "kind": "[]object"
    },
    {
      "path": "FacilitySpecification",
      "kind": "[]object"
    },
    {
      "path": "FacilityOperator",
      "kind": "[]object"
    },
    {
      "path": "SpatialLocation",
      "kind": "[]object"
    },
    {
      "path": "GeoContexts",
      "kind": "[]object"
    }
  ]
}