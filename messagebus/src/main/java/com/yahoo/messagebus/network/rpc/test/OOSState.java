// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class OOSState {
    private Map<String, Boolean> data = new LinkedHashMap<String, Boolean>();

    public OOSState add(String service, boolean oos) {
        data.put(service, oos);
        return this;
    }

    public Set<String> getServices() {
        return data.keySet();
    }

    public boolean isOOS(String service) {
        return data.containsKey(service) && data.get(service);
    }
}
