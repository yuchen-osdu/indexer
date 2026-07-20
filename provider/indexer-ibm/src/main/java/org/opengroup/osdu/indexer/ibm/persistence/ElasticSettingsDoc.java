/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.persistence;

import org.opengroup.osdu.indexer.ibm.model.ElasticSettingSchema;
import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ElasticSettingsDoc {
	
	public static final String DB_NAME =  "SearchSettings";   //collection name
	
    @Id
    private String _id;
    private String _rev;
    private ElasticSettingSchema settingSchema;
    
	public void setId(String id) {
		this._id = id;		
	}
	
}