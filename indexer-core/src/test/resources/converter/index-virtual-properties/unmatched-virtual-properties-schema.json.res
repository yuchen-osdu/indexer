{
  "kind" : "osdu:wks:master-data--Wellbore:1.0.0",
  "schema" : [ {
    "path" : "ResourceHomeRegionID",
    "kind" : "string"
  }, {
    "path" : "ResourceHostRegionIDs",
    "kind" : "[]string"
  }, {
    "path" : "ResourceLifecycleStatus",
    "kind" : "string"
  }, {
    "path" : "ResourceSecurityClassification",
    "kind" : "string"
  }, {
    "path" : "ResourceCurationStatus",
    "kind" : "string"
  }, {
    "path" : "ExistenceKind",
    "kind" : "string"
  }, {
    "path" : "TechnicalAssuranceID",
    "kind" : "string"
  }, {
    "path" : "Source",
    "kind" : "string"
  }, {
    "path" : "NameAliases",
    "kind" : "nested",
    "properties" : [ {
      "path" : "AliasNameTypeID",
      "kind" : "string"
    }, {
      "path" : "EffectiveDateTime",
      "kind" : "datetime"
    }, {
      "path" : "AliasName",
      "kind" : "string"
    }, {
      "path" : "TerminationDateTime",
      "kind" : "datetime"
    }, {
      "path" : "DefinitionOrganisationID",
      "kind" : "string"
    } ]
  }, {
    "path" : "SpatialLocation.SpatialParameterTypeID",
    "kind" : "string"
  }, {
    "path" : "SpatialLocation.QuantitativeAccuracyBandID",
    "kind" : "string"
  }, {
    "path" : "SpatialLocation.CoordinateQualityCheckRemarks",
    "kind" : "[]string"
  }, {
    "path" : "SpatialLocation.AppliedOperations",
    "kind" : "[]string"
  }, {
    "path" : "SpatialLocation.QualitativeSpatialAccuracyTypeID",
    "kind" : "string"
  }, {
    "path" : "SpatialLocation.CoordinateQualityCheckPerformedBy",
    "kind" : "string"
  }, {
    "path" : "SpatialLocation.SpatialLocationCoordinatesDate",
    "kind" : "datetime"
  }, {
    "path" : "SpatialLocation.CoordinateQualityCheckDateTime",
    "kind" : "datetime"
  }, {
    "path" : "SpatialLocation.Wgs84Coordinates",
    "kind" : "core:dl:geoshape:1.0.0"
  }, {
    "path" : "SpatialLocation.SpatialGeometryTypeID",
    "kind" : "string"
  }, {
    "path" : "VersionCreationReason",
    "kind" : "string"
  }, {
    "path" : "GeoContexts",
    "kind" : "nested",
    "properties" : [ {
      "path" : "GeoPoliticalEntityID",
      "kind" : "string"
    }, {
      "path" : "GeoTypeID",
      "kind" : "string"
    }, {
      "path" : "BasinID",
      "kind" : "string"
    }, {
      "path" : "GeoTypeID",
      "kind" : "string"
    }, {
      "path" : "FieldID",
      "kind" : "string"
    }, {
      "path" : "PlayID",
      "kind" : "string"
    }, {
      "path" : "GeoTypeID",
      "kind" : "string"
    }, {
      "path" : "ProspectID",
      "kind" : "string"
    }, {
      "path" : "GeoTypeID",
      "kind" : "string"
    } ]
  }, {
    "path" : "FacilityStates",
    "kind" : "nested",
    "properties" : [ {
      "path" : "EffectiveDateTime",
      "kind" : "datetime"
    }, {
      "path" : "FacilityStateTypeID",
      "kind" : "string"
    }, {
      "path" : "TerminationDateTime",
      "kind" : "datetime"
    } ]
  }, {
    "path" : "FacilityID",
    "kind" : "string"
  }, {
    "path" : "OperatingEnvironmentID",
    "kind" : "string"
  }, {
    "path" : "FacilityNameAliases",
    "kind" : "[]object"
  }, {
    "path" : "FacilityEvents",
    "kind" : "nested",
    "properties" : [ {
      "path" : "EffectiveDateTime",
      "kind" : "datetime"
    }, {
      "path" : "TerminationDateTime",
      "kind" : "datetime"
    }, {
      "path" : "FacilityEventTypeID",
      "kind" : "string"
    } ]
  }, {
    "path" : "FacilitySpecifications",
    "kind" : "flattened"
  }, {
    "path" : "DataSourceOrganisationID",
    "kind" : "string"
  }, {
    "path" : "InitialOperatorID",
    "kind" : "string"
  }, {
    "path" : "CurrentOperatorID",
    "kind" : "string"
  }, {
    "path" : "FacilityOperators",
    "kind" : "nested",
    "properties" : [ {
      "path" : "FacilityOperatorID",
      "kind" : "string"
    }, {
      "path" : "EffectiveDateTime",
      "kind" : "datetime"
    }, {
      "path" : "FacilityOperatorOrganisationID",
      "kind" : "string"
    }, {
      "path" : "TerminationDateTime",
      "kind" : "datetime"
    } ]
  }, {
    "path" : "FacilityName",
    "kind" : "string"
  }, {
    "path" : "FacilityTypeID",
    "kind" : "string"
  }, {
    "path" : "GeographicBottomHoleLocation.SpatialParameterTypeID",
    "kind" : "string"
  }, {
    "path" : "GeographicBottomHoleLocation.QuantitativeAccuracyBandID",
    "kind" : "string"
  }, {
    "path" : "GeographicBottomHoleLocation.CoordinateQualityCheckRemarks",
    "kind" : "[]string"
  }, {
    "path" : "GeographicBottomHoleLocation.AppliedOperations",
    "kind" : "[]string"
  }, {
    "path" : "GeographicBottomHoleLocation.QualitativeSpatialAccuracyTypeID",
    "kind" : "string"
  }, {
    "path" : "GeographicBottomHoleLocation.CoordinateQualityCheckPerformedBy",
    "kind" : "string"
  }, {
    "path" : "GeographicBottomHoleLocation.SpatialLocationCoordinatesDate",
    "kind" : "datetime"
  }, {
    "path" : "GeographicBottomHoleLocation.CoordinateQualityCheckDateTime",
    "kind" : "datetime"
  }, {
    "path" : "GeographicBottomHoleLocation.Wgs84Coordinates",
    "kind" : "core:dl:geoshape:1.0.0"
  }, {
    "path" : "GeographicBottomHoleLocation.SpatialGeometryTypeID",
    "kind" : "string"
  }, {
    "path" : "DrillingReasons",
    "kind" : "[]object"
  }, {
    "path" : "VerticalMeasurements",
    "kind" : "nested",
    "properties" : [ {
      "path" : "VerticalMeasurementID",
      "kind" : "string"
    }, {
      "path" : "WellboreTVDTrajectoryID",
      "kind" : "string"
    }, {
      "path" : "VerticalCRSID",
      "kind" : "string"
    }, {
      "path" : "VerticalMeasurementSourceID",
      "kind" : "string"
    }, {
      "path" : "VerticalReferenceID",
      "kind" : "string"
    }, {
      "path" : "TerminationDateTime",
      "kind" : "datetime"
    }, {
      "path" : "VerticalMeasurementPathID",
      "kind" : "string"
    }, {
      "path" : "EffectiveDateTime",
      "kind" : "datetime"
    }, {
      "path" : "VerticalMeasurement",
      "kind" : "double"
    }, {
      "path" : "VerticalMeasurementTypeID",
      "kind" : "string"
    }, {
      "path" : "VerticalMeasurementDescription",
      "kind" : "string"
    }, {
      "path" : "VerticalMeasurementUnitOfMeasureID",
      "kind" : "string"
    } ]
  }, {
    "path" : "PrimaryMaterialID",
    "kind" : "string"
  }, {
    "path" : "SequenceNumber",
    "kind" : "int"
  }, {
    "path" : "TargetFormation",
    "kind" : "string"
  }, {
    "path" : "KickOffWellbore",
    "kind" : "string"
  }, {
    "path" : "DefaultVerticalMeasurementID",
    "kind" : "string"
  }, {
    "path" : "ProjectedBottomHoleLocation.SpatialParameterTypeID",
    "kind" : "string"
  }, {
    "path" : "ProjectedBottomHoleLocation.QuantitativeAccuracyBandID",
    "kind" : "string"
  }, {
    "path" : "ProjectedBottomHoleLocation.CoordinateQualityCheckRemarks",
    "kind" : "[]string"
  }, {
    "path" : "ProjectedBottomHoleLocation.AppliedOperations",
    "kind" : "[]string"
  }, {
    "path" : "ProjectedBottomHoleLocation.QualitativeSpatialAccuracyTypeID",
    "kind" : "string"
  }, {
    "path" : "ProjectedBottomHoleLocation.CoordinateQualityCheckPerformedBy",
    "kind" : "string"
  }, {
    "path" : "ProjectedBottomHoleLocation.SpatialLocationCoordinatesDate",
    "kind" : "datetime"
  }, {
    "path" : "ProjectedBottomHoleLocation.CoordinateQualityCheckDateTime",
    "kind" : "datetime"
  }, {
    "path" : "ProjectedBottomHoleLocation.Wgs84Coordinates",
    "kind" : "core:dl:geoshape:1.0.0"
  }, {
    "path" : "ProjectedBottomHoleLocation.SpatialGeometryTypeID",
    "kind" : "string"
  }, {
    "path" : "WellID",
    "kind" : "string"
  }, {
    "path" : "TrajectoryTypeID",
    "kind" : "string"
  }, {
    "path" : "DefinitiveTrajectoryID",
    "kind" : "string"
  }, {
    "path" : "VirtualProperties.DefaultLocation.SpatialParameterTypeID",
    "kind" : "string"
  }, {
    "path" : "VirtualProperties.DefaultLocation.QuantitativeAccuracyBandID",
    "kind" : "string"
  }, {
    "path" : "VirtualProperties.DefaultLocation.CoordinateQualityCheckRemarks",
    "kind" : "[]string"
  }, {
    "path" : "VirtualProperties.DefaultLocation.AppliedOperations",
    "kind" : "[]string"
  }, {
    "path" : "VirtualProperties.DefaultLocation.QualitativeSpatialAccuracyTypeID",
    "kind" : "string"
  }, {
    "path" : "VirtualProperties.DefaultLocation.CoordinateQualityCheckPerformedBy",
    "kind" : "string"
  }, {
    "path" : "VirtualProperties.DefaultLocation.SpatialLocationCoordinatesDate",
    "kind" : "datetime"
  }, {
    "path" : "VirtualProperties.DefaultLocation.CoordinateQualityCheckDateTime",
    "kind" : "datetime"
  }, {
    "path" : "VirtualProperties.DefaultLocation.Wgs84Coordinates",
    "kind" : "core:dl:geoshape:1.0.0"
  }, {
    "path" : "VirtualProperties.DefaultLocation.SpatialGeometryTypeID",
    "kind" : "string"
  }, {
         "path" : "VirtualProperties.DefaultLocation.IsDecimated",
         "kind" : "boolean"
  }]
}
