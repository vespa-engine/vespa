// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.fs4.GetDocSumsPacket;
import com.yahoo.fs4.MapEncoder;
import com.yahoo.tensor.Tensor;
import com.yahoo.text.JSON;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contains the properties of a query.
 * This is a multimap: Multiple properties may be set for the same key.
 *
 * @author bratseth
 */
public class RankProperties implements Cloneable {

    private final Map<String, List<Object>> properties;

    public RankProperties() {
        this(new LinkedHashMap<>());
    }

    private RankProperties(Map<String, List<Object>> properties) {
        this.properties = properties;
    }

    public void put(String name, String value) {
        put(name, (Object)value);
    }

    /** Adds a property by full name to a value */
    public void put(String name, Object value) {
        List<Object> list = properties.get(name);
        if (list == null) {
            list = new ArrayList<>();
            properties.put(name, list);
        }
        list.add(value);
    }

    /**
     * Returns a read-only list of properties properties by full name.
     * If this is not set, null is returned. If this is explicitly set to
     * have no values, and empty list is returned.
     */
    public List<String> get(String name) {
        List<Object> values = properties.get(name);
        if (values == null) return null;
        if (values.isEmpty()) return Collections.<String>emptyList();

        // Compatibility ...
        List<String> stringValues = new ArrayList<>(values.size());
        for (Object value : values)
            stringValues.add(value.toString());
        return Collections.unmodifiableList(stringValues);
    }

    /**
     * Returns a tensor (as moved from RankFeatures by prepare step) if present
     *
     * @throws IllegalArgumentException if the value is there but wrong type
     */
    public Optional<Tensor> getAsTensor(String name) {
        List<Object> values = properties.get(name);
        if (values == null || values.isEmpty()) return Optional.empty();
        if (values.size() != 1) {
            throw new IllegalArgumentException("unexpected multiple [" + values.size() + "] values for property '" + name + "'");
        }
        Object feature = values.get(0);
        if (feature == null) return Optional.empty();
        if (feature instanceof Tensor t) return Optional.of(t);
        if (feature instanceof Double d) return Optional.of(Tensor.from(d));
        throw new IllegalArgumentException("Expected '" + name + "' to be a tensor or double, but it is '" + feature +
                "', this usually means that '" + name + "' is not defined in the schema. " +
                "See https://docs.vespa.ai/en/tensor-user-guide.html#querying-with-tensors");
    }

    /** Removes all properties for a given name */
    public void remove(String name) {
        properties.remove(name);
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }

    /** Returns a modifiable map of the properties of this */
    public Map<String, List<Object>> asMap() { return properties; }

    /** Encodes this in a binary internal representation and returns the number of property maps encoded (0 or 1) */
    public int encode(ByteBuffer buffer, boolean encodeQueryData) {
        if (encodeQueryData) {
            return MapEncoder.encodeMultiMap("rank", properties, buffer);
        }
        else {
            List<Object> sessionId = properties.get(GetDocSumsPacket.sessionIdKey);
            if (sessionId == null) return 0;
            return MapEncoder.encodeSingleValue("rank", GetDocSumsPacket.sessionIdKey, sessionId.get(0), buffer);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof RankProperties)) return false;

        return this.properties.equals(((RankProperties)other).properties);
    }

    @Override
    public int hashCode() {
        return properties.hashCode();
    }

    @Override
    public RankProperties clone() {
        Map<String, List<Object>> clone = new LinkedHashMap<>();
        for (Map.Entry<String, List<Object>> entry : properties.entrySet())
            clone.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        return new RankProperties(clone);
    }

    @Override
    public String toString() {
        return JSON.encode(properties);
    }

}
