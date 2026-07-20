/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm;

import org.opengroup.osdu.indexer.IndexerApplication;
import org.opengroup.osdu.indexer.ServerletInitializer;
import org.opengroup.osdu.indexer.service.ElasticSettingServiceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;


@SpringBootApplication
@ComponentScan(
		basePackages = {"org.opengroup.osdu"},
		excludeFilters = {
				@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value=IndexerApplication.class),
				@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value=ServerletInitializer.class),
				@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value=ElasticSettingServiceImpl.class),
		}
		)
@PropertySource("classpath:swagger.properties")
public class IndexerIBMApplication {
	
	public static void main(String[] args) throws Exception {
				
		SpringApplication.run(IndexerIBMApplication.class, args);
	
	}

}
