// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.HashMap;
import java.util.Map;

public class MockSecretStore implements SecretStore {
    private Map<String, Map<Integer, String>> secrets = new HashMap<>();

    @Override
    public String getSecret(String key) {
        int defaultVersion = 0;
        if(secrets.containsKey(key) && secrets.get(key).containsKey(defaultVersion))
            return secrets.get(key).get(defaultVersion);
        throw new RuntimeException("Key not found: " + key);
    }

    @Override
    public String getSecret(String key, int version) {
        return secrets.get(key).get(version);
    }

    public void put(String key, int version, String value) {
        secrets.computeIfAbsent(key, k -> new HashMap<>()).put(version, value);
    }

    public void remove(String key) {
        secrets.remove(key);
    }
}
