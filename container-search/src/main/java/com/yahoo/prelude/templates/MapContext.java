// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A context having a map as secondary storage
 * @deprecated use a Renderer instead
 */
@SuppressWarnings("deprecation")
@Deprecated // TODO: Remove on Vespa 7
public class MapContext extends Context {

    private Map<String, Object> map = new LinkedHashMap<>();

    @Override
    public Object get(String key) {
        return normalizeValue(map.get(key));
    }

    public Object put(String name, Object value) {
        return map.put(name, value);
    }

    public Object remove(Object name) {
        return map.remove(name);
    }

    @Override
    public Collection<? extends Object> getKeys() {
        return map.keySet();
    }

}
