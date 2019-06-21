// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.HashMap;
import java.util.Map;

public class MockSecretStore implements SecretStore {
    Map<String, String> secrets = new HashMap<>();

    @Override
    public String getSecret(String key) {
        if(secrets.containsKey(key))
            return secrets.get(key);
        throw new RuntimeException("Key not found: " + key);
    }

    @Override
    public String getSecret(String key, int version) {
        return getSecret(key);
    }

    public void put(String key, String value) {
        secrets.put(key, value);
    }

    public void remove(String key) {
        secrets.remove(key);
    }

    public void clear() {
        secrets.clear();
    }
}
