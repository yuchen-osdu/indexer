#/bin/bash

for f in $(find R3-json-schema -name '*.json');
  do python3 StorageSchemaGenerator.py $f;
done
