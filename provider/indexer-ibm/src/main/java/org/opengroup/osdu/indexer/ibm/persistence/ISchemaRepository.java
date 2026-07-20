/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.persistence;

import org.opengroup.osdu.indexer.ibm.model.ElasticSettingSchema;

public interface ISchemaRepository {
    String SCHEMA_KIND = "IndexerSchema";

    String SCHEMA = "schema";
    String KIND = "KIND";

    void add(ElasticSettingSchema schema, String id);

    ElasticSettingSchema get(String id);
}

