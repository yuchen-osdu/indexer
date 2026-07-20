/* Licensed Materials - Property of IBM              */		
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.indexer.ibm.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.search.IdToken;
import org.opengroup.osdu.core.common.provider.interfaces.IJwtCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtCache implements IJwtCache<String, IdToken> {
    private VmCache<String, IdToken> cache;

    // Azure service account id_token can be requested only for 1 hr
    private final static int EXPIRED_AFTER = 59;

    public JwtCache(@Value("${MAX_CACHE_VALUE_SIZE}") final String MAX_CACHE_VALUE_SIZE) {
        cache = new VmCache<>(EXPIRED_AFTER * 60, Integer.parseInt(MAX_CACHE_VALUE_SIZE));
    }

    @Override
    public void put(String s, IdToken o) {
        this.cache.put(s, o);
    }

    @Override
    public IdToken get(String s) {
        return this.cache.get(s);
    }

    @Override
    public void delete(String s) {
        this.cache.delete(s);
    }

    @Override
    public void clearAll() {
        this.cache.clearAll();
    }
}
