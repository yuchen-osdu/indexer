# Introduction 

This document explains how to check R3 json schema set that is planned for the R3 delivery

## Steps

1) Put files with schemas to 'R3-json-schema/Generated' folder
2) Run gen-for-folder.sh script. 
It uses  StorageSchemaGenerator.py python script developed by Thomas Gehrmann <gehrmann@slb.com> to generate corresponding files in storage schema format with '.res' extension.
You need python 3.6 for that.
3) Run folderPassed() unit test. It generates schemas with Java converter and compares results with .res files.







