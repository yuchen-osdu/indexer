/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.model;

import jakarta.validation.constraints.NotEmpty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ElasticSettingSchema {

    @NotEmpty
    private String host;

    @NotEmpty
    private String port;

    @NotEmpty
    private String usernameAndPassword;

    @NotEmpty
    private boolean isHttps;  

}
