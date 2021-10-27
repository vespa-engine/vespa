// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi.mock;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.HashMap;
import java.util.Map;

/**
 * @author mpolden
 */
public class SecretStoreMock extends AbstractComponent implements SecretStore {

    private final Map<String, String> secrets = new HashMap<>();

    public SecretStoreMock setSecret(String key, String value) {
        secrets.put(key, value);
        return this;
    }

    @Override
    public String getSecret(String key) {
        if (!secrets.containsKey(key)) throw new RuntimeException("No such key '" + key + "'");
        return secrets.get(key);
    }

    @Override
    public String getSecret(String key, int version) {
        if (!secrets.containsKey(key)) throw new RuntimeException("No such key '" + key + "'");
        return secrets.get(key);
    }

}
