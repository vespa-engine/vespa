// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.tensor.TensorType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A context backed by a Map
 *
 * @author bratseth
 */
public class MapContext extends Context {

    private Map<String, Value> bindings = new HashMap<>();

    private boolean frozen = false;

    public MapContext() {
    }

    /**
     * Creates a map context from a map.
     * All the Values of the map will be frozen.
     */
    public MapContext(Map<String,Value> bindings) {
        bindings.forEach((k, v) -> this.bindings.put(k, v.freeze()));
    }

    /**
     * Freezes this.
     * Returns this for convenience.
     */
    public MapContext freeze() {
        if ( ! frozen)
            bindings = Collections.unmodifiableMap(bindings);
        return this;
    }

    /** Returns the type of the given value key, or null if it is not bound. */
    @Override
    public TensorType getType(String key) {
        Value value = bindings.get(key);
        if (value == null) return null;
        return value.type();
    }

    /** Returns the value of a key. 0 is returned if the given key is not bound in this. */
    @Override
    public Value get(String key) {
        return bindings.getOrDefault(key, DoubleValue.zero);
    }

    /**
     * Sets the value of a key. The value is frozen by this.
     */
    @Override
    public void put(String key,Value value) {
        bindings.put(key, value.freeze());
    }

    /** Returns an immutable view of the bindings of this. */
    public Map<String, Value> bindings() {
        if (frozen) return bindings;
        return Collections.unmodifiableMap(bindings);
    }

    /** Returns a new, modifiable context containing all the bindings of this */
    public MapContext thawedCopy() { return new MapContext(new HashMap<>(bindings)); }

    /** Returns an unmodifiable map of the names of this */
    @Override
    public Set<String> names() {
        if (frozen) return bindings.keySet();
        return Collections.unmodifiableMap(bindings).keySet();
    }

    @Override
    public String toString() {
        return "a map context [" + bindings.size() + " bindings]";
    }

    /**
     * A convenience constructor which returns a map context from a string on the form
     * <code>name1:value1, name2:value2 ...</code>.
     * Extra spaces are allowed anywhere. Any other deviation from the syntax causes an exception to be thrown.
     */
    public static MapContext fromString(String contextString) {
        MapContext mapContext = new MapContext();
        for (String keyValueString : contextString.split(",")) {
            String[] strings = keyValueString.trim().split(":");
            mapContext.put(strings[0].trim(), Value.parse(strings[1].trim()));
        }
        return mapContext;
    }

}
