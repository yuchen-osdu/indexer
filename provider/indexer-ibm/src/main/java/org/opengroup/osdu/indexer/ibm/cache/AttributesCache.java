/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.provider.interfaces.IAttributesCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AttributesCache implements IAttributesCache<String,Set> {

    private VmCache<String, Set> cache;

    public AttributesCache(@Value("${INDEX_CACHE_EXPIRATION}") final String INDEX_CACHE_EXPIRATION,
                           @Value("${MAX_CACHE_VALUE_SIZE}") final String MAX_CACHE_VALUE_SIZE) {
        cache = new VmCache<>(Integer.parseInt(INDEX_CACHE_EXPIRATION) * 60,
                Integer.parseInt(MAX_CACHE_VALUE_SIZE));
    }

    @Override
    public void put(String key, Set value) {
        this.cache.put(key, value);
    }

    @Override
    public Set get(String key) {
        return this.cache.get(key);
    }

    @Override
    public void delete(String key) {
        this.cache.delete(key);
    }

    @Override
    public void clearAll() {
        this.cache.clearAll();
    }

}