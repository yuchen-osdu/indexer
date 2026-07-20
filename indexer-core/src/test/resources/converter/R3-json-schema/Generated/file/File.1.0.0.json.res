{
  "kind": "osdu:osdu:Wellbore:1.0.0",
  "schema": [
    {
      "kind": "link",
      "path": "SchemaFormatTypeID"
    },
    {
      "kind": "string",
      "path": "PreloadFilePath"
    },
    {
      "kind": "string",
      "path": "FileSource"
    },
    {
      "kind": "int",
      "path": "FileSize"
    },
    {
      "kind": "link",
      "path": "EncodingFormatTypeID"
    },
    {
      "kind": "string",
      "path": "Endian"
    },
    {
      "kind": "bool",
      "path": "LossyCompressionIndicator"
    },
    {
      "kind": "link",
      "path": "CompressionMethodTypeID"
    },
    {
      "kind": "double",
      "path": "CompressionLevel"
    },
    {
      "kind": "string",
      "path": "Checksum"
    },
    {
      "kind": "[]object",
      "path": "VectorHeaderMapping"
    }
  ]
}
