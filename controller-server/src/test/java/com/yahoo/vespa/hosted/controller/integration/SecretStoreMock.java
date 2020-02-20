// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author mpolden
 */
public class SecretStoreMock extends AbstractComponent implements SecretStore {

    private final Map<String, TreeMap<Integer, String>> secrets = new HashMap<>();

    public SecretStoreMock setSecret(String name, String value, int version) {
        TreeMap<Integer, String> values = secrets.getOrDefault(name, new TreeMap<>());
        values.put(version, value);
        secrets.put(name, values);
        return this;
    }

    public SecretStoreMock setSecret(String name, String value) {
        return setSecret(name, value, 1);
    }

    public SecretStoreMock clear() {
        secrets.clear();
        return this;
    }

    @Override
    public String getSecret(String key) {
        TreeMap<Integer, String> values = secrets.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.lastEntry().getValue();
    }

    @Override
    public String getSecret(String key, int version) {
        return secrets.getOrDefault(key, new TreeMap<>()).get(version);
    }

    @Override
    public List<Integer> listSecretVersions(String key) {
        return List.copyOf(secrets.getOrDefault(key, new TreeMap<>()).keySet());
    }
}
