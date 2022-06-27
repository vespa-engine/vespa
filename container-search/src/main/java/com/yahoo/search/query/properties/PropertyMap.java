// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.query.properties;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Properties;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;

/**
 * A Map backing of Properties.
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

    private final static CloneHelper cloneHelper = new CloneHelper();

    /** The properties of this */
    private Map<CompoundName, Object> properties = new LinkedHashMap<>();

    public void set(CompoundName name, Object value, Map<String, String> context) {
        if (value == null) // Both clear and forward
            properties.remove(name);

        if (shouldSet(name, value))
            properties.put(name, value);
        else
            super.set(name, value, context);
    }

    /**
     * Return true if this value should be set in this map, false if the set should be propagated instead
     * This default implementation always returns true.
     */
    protected boolean shouldSet(CompoundName name, Object value) { return true; }

    @Override
    public Object get(CompoundName name, Map<String,String> context,
                                com.yahoo.processing.request.Properties substitution) {
        if ( ! properties.containsKey(name)) return super.get(name,context,substitution);
        return properties.get(name);
    }

    /**
     * Returns a direct reference to the map containing the properties set in this instance.
     */
    public Map<CompoundName, Object> propertyMap() {
        return properties;
    }

    @Override
    public PropertyMap clone() {
        PropertyMap clone = (PropertyMap)super.clone();
        clone.properties = new HashMap<>();
        for (Map.Entry<CompoundName, Object> entry : this.properties.entrySet()) {
            Object cloneValue = cloneHelper.clone(entry.getValue());
            if (cloneValue == null)
                cloneValue = entry.getValue(); // Shallow copy objects which does not support cloning
            clone.properties.put(entry.getKey(), cloneValue);
        }
        return clone;
    }

    @Override
    public Map<String, Object> listProperties(CompoundName path, Map<String, String> context, com.yahoo.processing.request.Properties substitution) {
        Map<String, Object> map = super.listProperties(path, context, substitution);

        for (Map.Entry<CompoundName, Object> entry : properties.entrySet()) {
            if ( ! entry.getKey().hasPrefix(path)) continue;
            CompoundName propertyName = entry.getKey().rest(path.size());
            if (propertyName.isEmpty()) continue;
            map.put(propertyName.toString(), entry.getValue());
        }
        return map;
    }

    /** Clones this object if it is clonable, and the clone is public. Returns null if not */
    public static Object clone(Object object) {
        return cloneHelper.clone(object);
    }

}
