// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Simon Thoresen Hult
 */
public class SlobrokState {
    private Map<String, Integer> data = new LinkedHashMap<String, Integer>();

    public SlobrokState add(String pattern, int count) {
        data.put(pattern, count);
        return this;
    }

    public Set<String> getPatterns() {
        return data.keySet();
    }

    public int getCount(String pattern) {
        return data.containsKey(pattern) ? data.get(pattern) : 1;
    }
}
