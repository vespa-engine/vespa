// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.properties;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.Properties;

import java.util.HashMap;
import java.util.Map;

/**
 * A HashMap backing of Properties.
 * <p>
 * When this is cloned it will deep copy not only the model object map, but also each
 * clonable member inside the map.
 * <p>
 * Subclassing is supported, a hook can be implemented to provide conditional inclusion in the map.
 * By default - all properties are accepted, so set is never propagated.
 * <p>
 * This class is not multithread safe.
 *
 * @author bratseth
 */
public class PropertyMap extends Properties {

    /**
     * The properties of this
     */
    private Map<CompoundName, Object> properties = new HashMap<>();

    public void set(CompoundName name, Object value, Map<String, String> context) {
        if (shouldSet(name, value))
            properties.put(name, value);
        else
            super.set(name, value, context);
    }

    /**
     * Return true if this value should be set in this map, false if the set should be propagated instead
     * This default implementation always returns true.
     */
    protected boolean shouldSet(CompoundName name, Object value) {
        return true;
    }

    public
    @Override
    Object get(CompoundName name, Map<String, String> context,
               com.yahoo.processing.request.Properties substitution) {
        if (!properties.containsKey(name)) return super.get(name, context, substitution);
        return properties.get(name);
    }

    public
    @Override
    PropertyMap clone() {
        PropertyMap clone = (PropertyMap) super.clone();
        clone.properties = cloneMap(this.properties);
        return clone;
    }

    @Override
    public Map<String, Object> listProperties(CompoundName path, Map<String, String> context, Properties substitution) {
        Map<String, Object> map = super.listProperties(path, context, substitution);

        for (Map.Entry<CompoundName, Object> entry : properties.entrySet()) {
            if ( ! entry.getKey().hasPrefix(path)) continue;
            CompoundName propertyName = entry.getKey().rest(path.size());
            if (propertyName.isEmpty()) continue;
            map.put(propertyName.toString(), entry.getValue());
        }
        return map;
    }

}
