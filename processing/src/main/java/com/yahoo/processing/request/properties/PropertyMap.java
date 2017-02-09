// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.properties;

import com.yahoo.collections.MethodCache;
import com.yahoo.component.provider.FreezableClass;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.Properties;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.logging.Logger;

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

    private static Logger log = Logger.getLogger(PropertyMap.class.getName());
    private static final MethodCache cloneMethodCache = new MethodCache("clone");

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

    /**
     * Clones a map by deep cloning each value which is cloneable and shallow copying all other values.
     */
    public static Map<CompoundName, Object> cloneMap(Map<CompoundName, Object> map) {
        Map<CompoundName, Object> cloneMap = new HashMap<>();
        for (Map.Entry<CompoundName, Object> entry : map.entrySet()) {
            Object cloneValue = clone(entry.getValue());
            if (cloneValue == null)
                cloneValue = entry.getValue(); // Shallow copy objects which does not support cloning
            cloneMap.put(entry.getKey(), cloneValue);
        }
        return cloneMap;
    }

    /**
     * Clones this object if it is clonable, and the clone is public. Returns null if not
     */
    public static Object clone(Object object) {
        if (object == null) return null;
        if (!(object instanceof Cloneable)) return null;
        if (object instanceof Object[])
            return arrayClone((Object[]) object);
        else
            return objectClone(object);
    }

    private static Object arrayClone(Object[] object) {
        Object[] arrayClone = Arrays.copyOf(object, object.length);
        // deep clone
        for (int i = 0; i < arrayClone.length; i++) {
            Object elementClone = clone(arrayClone[i]);
            if (elementClone != null)
                arrayClone[i] = elementClone;
        }
        return arrayClone;
    }

    private static Object objectClone(Object object) {
        // Fastpath for our own commonly used classes
        if (object instanceof FreezableClass) {
            // List common superclass of 'com.yahoo.search.result.Hit'
            return ((FreezableClass) object).clone();
        }
        else if (object instanceof PublicCloneable) {
            return ((PublicCloneable)object).clone();
        }
        else if (object instanceof LinkedList) { // TODO: Why? Somebody's infatuation with LinkedList knows no limits
            return ((LinkedList) object).clone();
        }
        else if (object instanceof ArrayList) { // TODO: Why? Likewise
            return ((ArrayList) object).clone();
        }

        try {
            Method cloneMethod = cloneMethodCache.get(object);
            if (cloneMethod == null) {
                log.warning("'" + object + "' is Cloneable, but has no clone method - will use the same instance in all requests");
                return null;
            }
            return cloneMethod.invoke(object);
        } catch (IllegalAccessException e) {
            log.warning("'" + object + "' is Cloneable, but clone method cannot be accessed - will use the same instance in all requests");
            return null;
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Exception cloning '" + object + "'", e);
        }
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
