// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * The properties of a request
 *
 * @author bratseth
 */
public class Properties implements Cloneable {

    private final static CloneHelper cloneHelper = new CloneHelper();
    private Properties chained = null;

    /**
     * Sets the properties chained to this.
     *
     * @param chained the properties to chain to this, or null to make this the last in the chain
     * @return the given chained object to allow setting up a chain by dotting in one statement
     */
    public Properties chain(Properties chained) {
        this.chained = chained;
        return chained;
    }

    /**
     * Returns the properties chained to this, or null if this is the last in the chain
     */
    public Properties chained() {
        return chained;
    }

    /**
     * Returns the first instance of the given class in this chain, or null if none
     */
    @SuppressWarnings("unchecked")
    public final <T extends Properties> T getInstance(Class<T> propertyClass) {
        if (propertyClass.isAssignableFrom(this.getClass())) return (T) this;
        if (chained == null) return null;
        return chained.getInstance(propertyClass);
    }

    /**
     * Lists all properties of this with no context, by delegating to listProperties("")
     */
    public final Map<String, Object> listProperties() {
        return listProperties(CompoundName.empty);
    }

    /**
     * Returns a snapshot of all properties of this - same as listProperties("",context)
     */
    public final Map<String, Object> listProperties(Map<String, String> context) {
        return listProperties(CompoundName.empty, context, this);
    }

    /**
     * Returns a snapshot of all properties by calling listProperties(path,null)
     */
    public final Map<String, Object> listProperties(CompoundName path) {
        return listProperties(path, null, this);
    }

    /**
     * Returns a snapshot of all properties by calling listProperties(path,null)
     */
    public final Map<String, Object> listProperties(String path) {
        return listProperties(new CompoundName(path), null, this);
    }

    /**
     * Returns a snapshot of all properties by calling listProperties(path,null)
     */
    public final Map<String, Object> listProperties(CompoundName path, Map<String, String> context) {
        return listProperties(path, context, this);
    }

    /**
     * Returns a snapshot of all properties by calling listProperties(path,null)
     */
    public final Map<String, Object> listProperties(String path, Map<String, String> context) {
        return listProperties(new CompoundName(path), context, this);
    }

    /**
     * Returns a snapshot of all properties of this having a given path prefix
     * <p>
     * Some sources of properties may not be list-able (e.g those using reflection)
     * and will not be included in this snapshot.
     *
     *
     * @param path         the prefix (up to a ".") of the properties to return, or null or the empty string to return all properties
     * @param context      the context used to resolve the properties, or null if none
     * @param substitution the properties which will be used to do string substitution in the values added to the map
     */
    public Map<String, Object> listProperties(CompoundName path, Map<String, String> context, Properties substitution) {
        if (path == null)
            path = CompoundName.empty;
        if (chained() == null)
            return new HashMap<>();
        else
            return chained().listProperties(path, context, substitution);
    }

    /**
     * Returns a snapshot of all properties of this having a given path prefix
     * <p>
     * Some sources of properties may not be list-able (e.g those using reflection)
     * and will not be included in this snapshot.
     *
     *
     * @param path         the prefix (up to a ".") of the properties to return, or null or the empty string to return all properties
     * @param context      the context used to resolve the properties, or null if none
     * @param substitution the properties which will be used to do string substitution in the values added to the map
     */
    public final Map<String, Object> listProperties(String path, Map<String, String> context, Properties substitution) {
        return listProperties(new CompoundName(path), context, substitution);
    }

    /**
     * Gets a named value which (if necessary) is resolved using a property context.
     *
     * @param name         the name of the property to return
     * @param context      the variant resolution context, or null if none
     * @param substitution the properties used to substitute in these properties, or null if none
     */
    public Object get(CompoundName name, Map<String, String> context, Properties substitution) {
        if (chained == null) return null;
        return chained.get(name, context, substitution);
    }

    /**
     * Gets a named value which (if necessary) is resolved using a property context
     *
     * @param name         the name of the property to return
     * @param context      the variant resolution context, or null if none
     * @param substitution the properties used to substitute in these properties, or null if none
     */
    public final Object get(String name, Map<String, String> context, Properties substitution) {
        return get(new CompoundName(name), context, substitution);
    }

    /**
     * Gets a named value from the first chained instance which has one by calling get(name,context,this)
     */
    public final Object get(CompoundName name, Map<String, String> context) {
        return get(name, context, this);
    }

    /**
     * Gets a named value from the first chained instance which has one by calling get(name,context,this)
     */
    public final Object get(String name, Map<String, String> context) {
        return get(new CompoundName(name), context, this);
    }

    /**
     * Gets a named value from the first chained instance which has one by calling get(name,null,this)
     */
    public final Object get(CompoundName name) {
        return get(name, null, this);
    }

    /**
     * Gets a named value from the first chained instance which has one by calling get(name,null,this)
     */
    public final Object get(String name) {
        return get(new CompoundName(name), null, this);
    }

    /**
     * Gets a named value from the first chained instance which has one,
     * or the default value if no value is set, or if the first value encountered is explicitly set to null.
     * <p>
     * This default implementation simply forwards to the chained instance, or returns the default if none
     *
     *
     * @param name         the name of the property to return
     * @param defaultValue the default value returned if the value returned is null
     */
    public final Object get(CompoundName name, Object defaultValue) {
        Object value = get(name);
        if (value == null) return defaultValue;
        return value;
    }

    /**
     * Gets a named value from the first chained instance which has one,
     * or the default value if no value is set, or if the first value encountered is explicitly set to null.
     * <p>
     * This default implementation simply forwards to the chained instance, or returns the default if none
     *
     * @param name         the name of the property to return
     * @param defaultValue the default value returned if the value returned is null
     */
    public final Object get(String name, Object defaultValue) {
        return get(new CompoundName(name), defaultValue);
    }

    /**
     * Sets a value to the first chained instance which accepts it.
     * <p>
     * This default implementation forwards to the chained instance or throws
     * a RuntimeException if there is not chained instance.
     *
     * @param name    the name of the value
     * @param value   the value to set. Setting a name to null explicitly is legal.
     * @param context the context used to resolve where the values should be set, or null if none
     * @throws RuntimeException if no instance in the chain accepted this name-value pair
     */
    public void set(CompoundName name, Object value, Map<String, String> context) {
        if (chained == null) throw new RuntimeException("Property '" + name + "->" + value +
                "' was not accepted in this property chain");
        chained.set(name, value, context);
    }

    /**
     * Sets a value to the first chained instance which accepts it.
     * <p>
     * This default implementation forwards to the chained instance or throws
     * a RuntimeException if there is not chained instance.
     *
     * @param name    the name of the value
     * @param value   the value to set. Setting a name to null explicitly is legal.
     * @param context the context used to resolve where the values should be set, or null if none
     * @throws RuntimeException if no instance in the chain accepted this name-value pair
     */
    public final void set(String name, Object value, Map<String, String> context) {
        set(new CompoundName(name), value, context);
    }

    /**
     * Sets a value to the first chained instance which accepts it by calling set(name,value,null).
     *
     * @param name  the name of the value
     * @param value the value to set. Setting a name to null explicitly is legal.
     * @throws RuntimeException if no instance in the chain accepted this name-value pair
     */
    public final void set(CompoundName name, Object value) {
        set(name, value, null);
    }

    /**
     * Sets a value to the first chained instance which accepts it by calling set(name,value,null).
     *
     * @param name  the name of the value
     * @param value the value to set. Setting a name to null explicitly is legal.
     * @throws RuntimeException if no instance in the chain accepted this name-value pair
     */
    public final void set(String name, Object value) {
        set(new CompoundName(name), value, Collections.<String,String>emptyMap());
    }

    /**
     * Gets a property as a boolean - if this value can reasonably be interpreted as a boolean, this will return
     * the value. Returns false if this property is null.
     */
    public final boolean getBoolean(CompoundName name) {
        return getBoolean(name, false);
    }

    /**
     * Gets a property as a boolean - if this value can reasonably be interpreted as a boolean, this will return
     * the value. Returns false if this property is null.
     */
    public final boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    /**
     * Gets a property as a boolean.
     * This will return true only if the value is either the empty string,
     * or any Object which has a toString which is case-insensitive equal to "true"
     *
     * @param defaultValue the value to return if this property is null
     */
    public final boolean getBoolean(CompoundName key, boolean defaultValue) {
        return asBoolean(get(key), defaultValue);
    }

    /**
     * Gets a property as a boolean.
     * This will return true only if the value is either the empty string,
     * or any Object which has a toString which is case-insensitive equal to "true"
     *
     * @param defaultValue the value to return if this property is null
     */
    public final boolean getBoolean(String key, boolean defaultValue) {
        return asBoolean(get(key), defaultValue);
    }

    /**
     * Converts a value to boolean - this will be true only if the value is either the empty string,
     * or any Object which has a toString which is case-insensitive equal to "true"
     */
    protected final boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;

        String s = value.toString();
        int sz = s.length();
        switch (sz) {
            case 0:
                return true;
            case 4:
                return ((s.charAt(0) | 0x20) == 't') &&
                        ((s.charAt(1) | 0x20) == 'r') &&
                        ((s.charAt(2) | 0x20) == 'u') &&
                        ((s.charAt(3) | 0x20) == 'e');
        }
        return false;
    }

    /**
     * Returns this property as a string
     *
     * @return this property as a string, or null if the property is null
     */
    public final String getString(CompoundName key) {
        return getString(key, null);
    }

    /**
     * Returns this property as a string
     *
     * @return this property as a string, or null if the property is null
     */
    public final String getString(String key) {
        return getString(key, null);
    }

    /**
     * Returns this property as a string
     *
     * @param key          the property key
     * @param defaultValue the value to return if this property is null
     * @return this property as a string
     */
    public final String getString(CompoundName key, String defaultValue) {
        return asString(get(key), defaultValue);
    }

    /**
     * Returns this property as a string
     *
     * @param key          the property key
     * @param defaultValue the value to return if this property is null
     * @return this property as a string
     */
    public final String getString(String key, String defaultValue) {
        return asString(get(key), defaultValue);
    }

    protected final String asString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        return value.toString();
    }

    /**
     * Returns a property as an Integer
     *
     * @return the integer value of the name, or null if the property is null
     * @throws NumberFormatException if the given parameter exists but
     *                               have a toString which is not parseable as a number
     */
    public final Integer getInteger(CompoundName name) {
        return getInteger(name, null);
    }

    /**
     * Returns a property as an Integer
     *
     * @return the integer value of the name, or null if the property is null
     * @throws NumberFormatException if the given parameter exists but
     *                               have a toString which is not parseable as a number
     */
    public final Integer getInteger(String name) {
        return getInteger(name, null);
    }

    /**
     * Returns a property as an Integer
     *
     * @param name         the property name
     * @param defaultValue the value to return if this property is null
     * @return the integer value for the name
     * @throws NumberFormatException if the given parameter does not exist
     *                               or does not have a toString parseable as a number
     */
    public final Integer getInteger(CompoundName name, Integer defaultValue) {
        return asInteger(get(name), defaultValue);
    }

    /**
     * Returns a property as an Integer
     *
     * @param name         the property name
     * @param defaultValue the value to return if this property is null
     * @return the integer value for the name
     * @throws NumberFormatException if the given parameter does not exist
     *                               or does not have a toString parseable as a number
     */
    public final Integer getInteger(String name, Integer defaultValue) {
        return asInteger(get(name), defaultValue);
    }

    protected final Integer asInteger(Object value, Integer defaultValue) {
        try {
            if (value == null)
                return defaultValue;

            if (value instanceof Integer)
                return (Integer) value;

            String stringValue = value.toString();
            if (stringValue.isEmpty())
                return defaultValue;

            return new Integer(stringValue);
        } catch (IllegalArgumentException e) {
            throw new NumberFormatException("Not a valid integer");
        }
    }

    /**
     * Returns a property as a Long
     *
     * @return the long value of the name, or null if the property is null
     * @throws NumberFormatException if the given parameter exists but have a value which
     *                               is not parseable as a number
     */
    public final Long getLong(CompoundName name) {
        return getLong(name, null);
    }

    /**
     * Returns a property as a Long
     *
     * @return the long value of the name, or null if the property is null
     * @throws NumberFormatException if the given parameter exists but have a value which
     *                               is not parseable as a number
     */
    public final Long getLong(String name) {
        return getLong(name, null);
    }

    /**
     * Returns a property as a Long
     *
     * @param name         the property name
     * @param defaultValue the value to return if this property is null
     * @return the integer value for this name
     * @throws NumberFormatException if the given parameter exists but have a value which
     *                               is not parseable as a number
     */
    public final Long getLong(CompoundName name, Long defaultValue) {
        return asLong(get(name), defaultValue);
    }

    /**
     * Returns a property as a Long
     *
     * @param name         the property name
     * @param defaultValue the value to return if this property is null
     * @return the integer value for this name
     * @throws NumberFormatException if the given parameter exists but have a value which
     *                               is not parseable as a number
     */
    public final Long getLong(String name, Long defaultValue) {
        return asLong(get(name), defaultValue);
    }

    protected final Long asLong(Object value, Long defaultValue) {
        try {
            if (value == null)
                return defaultValue;

            if (value instanceof Long)
                return (Long) value;

            String stringValue = value.toString();
            if (stringValue.isEmpty())
                return defaultValue;

            return new Long(value.toString());
        } catch (IllegalArgumentException e) {
            throw new NumberFormatException("Not a valid long");
        }
    }

    /**
     * Returns a property as a Double
     *
     * @return the integer value of the name, or null if the property is null
     * @throws NumberFormatException if the given parameter exists but have a value which
     *                               is not parseable as a number
     */
    public final Double getDouble(CompoundName name) {
        return getDouble(name, null);
    }

    /**
     * Returns a property as a Double
     *
     * @return the integer value of the name, or null if the property is null
     * @throws NumberFormatException if the given parameter exists but have a value which
     *                               is not parseable as a number
     */
    public final Double getDouble(String name) {
        return getDouble(name, null);
    }

    /**
     * Returns a property as a Double
     *
     * @param name         the property name
     * @param defaultValue the value to return if this property is null
     * @return the integer value for this name
     * @throws NumberFormatException if the given parameter exists but have a value which
     *                               is not parseable as a number
     */
    public final Double getDouble(CompoundName name, Double defaultValue) {
        return asDouble(get(name), defaultValue);
    }

    /**
     * Returns a property as a Double
     *
     * @param name         the property name
     * @param defaultValue the value to return if this property is null
     * @return the integer value for this name
     * @throws NumberFormatException if the given parameter exists but have a value which
     *                               is not parseable as a number
     */
    public final Double getDouble(String name, Double defaultValue) {
        return asDouble(get(name), defaultValue);
    }

    protected final Double asDouble(Object value, Double defaultValue) {
        try {
            if (value == null)
                return defaultValue;

            if (value instanceof Double)
                return (Double) value;

            String stringValue = value.toString();
            if (stringValue.isEmpty())
                return defaultValue;

            return new Double(value.toString());
        } catch (IllegalArgumentException e) {
            throw new NumberFormatException("Not a valid double");
        }
    }

    /**
     * Clones this instance and recursively all chained instance.
     * Implementations should call this and clone their own state as appropriate
     */
    public Properties clone() {
        try {
            Properties clone = (Properties) super.clone();
            if (chained != null)
                clone.chained = this.chained.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Will never happen");
        }
    }

    /**
     * Clones a map by deep cloning each value which is cloneable and shallow copying all other values.
     */
    public static Map<CompoundName, Object> cloneMap(Map<CompoundName, Object> map) {
        return cloneHelper.cloneMap(map);
    }
    /** Clones this object if it is clonable, and the clone is public. Returns null if not */
    public static Object clone(Object object) {
        return cloneHelper.clone(object);
    }
}
