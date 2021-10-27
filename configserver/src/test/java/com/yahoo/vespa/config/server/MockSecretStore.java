// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.HashMap;
import java.util.Map;

public class MockSecretStore implements SecretStore {
    final Map<String, Map<Integer, String>> secrets = new HashMap<>();

    @Override
    public String getSecret(String key) {
        if(secrets.containsKey(key))
            return secrets.get(key).get(0);
        throw new RuntimeException("Key not found: " + key);
    }

    @Override
    public String getSecret(String key, int version) {
        return secrets.get(key).get(version);
    }

    public void put(String key, int version, String value) {
        secrets.computeIfAbsent(key, k -> new HashMap<>()).put(version, value);
    }

    public void put(String key, String value) {
        put(key, 0, value);
    }

    public void remove(String key) {
        secrets.remove(key);
    }

    public void clear() {
        secrets.clear();
    }
}
