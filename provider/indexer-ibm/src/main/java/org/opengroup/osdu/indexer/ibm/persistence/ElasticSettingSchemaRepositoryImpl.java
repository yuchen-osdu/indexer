/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.persistence;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.ibm.auth.ServiceCredentials;
import org.opengroup.osdu.core.ibm.cloudant.IBMCloudantClientFactory;
import org.opengroup.osdu.indexer.ibm.model.ElasticSettingSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.cloudant.client.api.Database;


@Repository
public class ElasticSettingSchemaRepositoryImpl implements ISchemaRepository {

	@Value("${ibm.db.url}") 
	private String dbUrl;
	@Value("${ibm.db.apikey:#{null}}")
	private String apiKey;
	@Value("${ibm.db.user:#{null}}")
	private String dbUser;
	@Value("${ibm.db.password:#{null}}")
	private String dbPassword;
	
	@Value("${ibm.env.prefix:local-dev}")
	private String dbNamePrefix;
	
	private IBMCloudantClientFactory cloudantFactory;
	private Database db;
	
	@Inject
	private JaxRsDpsLog logger;

    @PostConstruct
    public void init(){
        try {
        	if (apiKey != null) {
    			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, apiKey));
    		} else {
    			cloudantFactory = new IBMCloudantClientFactory(new ServiceCredentials(dbUrl, dbUser, dbPassword));
    		}        	db = cloudantFactory.getDatabase(dbNamePrefix, "SearchSettings");
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

	@Override
	public void add(ElasticSettingSchema schema, String id) {
	  ElasticSettingsDoc sd = new ElasticSettingsDoc();
	  sd.setId(id);
	  sd.setSettingSchema(schema);
	  db.save(sd);
	}

    @Override
    public ElasticSettingSchema get(String id) {
    	if (db.contains(id)) {
    		ElasticSettingsDoc sd = db.find(ElasticSettingsDoc.class, id);
    		ElasticSettingSchema newSchema = new ElasticSettingSchema();
    		newSchema.setPort(sd.getSettingSchema().getPort());
    		newSchema.setHost(sd.getSettingSchema().getHost());
    		newSchema.setUsernameAndPassword(sd.getSettingSchema().getUsernameAndPassword());
    		newSchema.setHttps(sd.getSettingSchema().isHttps());
    		return newSchema;
		} else {
			logger.error(ElasticSettingsDoc.class + " with id " + id + " was not found in the database.");
			return null;
		}
    }

}
