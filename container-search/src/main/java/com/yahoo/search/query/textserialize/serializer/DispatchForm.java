// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.serializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Tony Vaagenes
 */
public class DispatchForm {
    private final String name;
    public final Map<Object, Object> properties = new LinkedHashMap<>();
    public final List<Object> children = new ArrayList<>();

    public DispatchForm(String name) {
        this.name = name;
    }

    public void addChild(Object child) {
        children.add(child);
    }

    /**
     * Only public for the purpose of testing.
     */
    public String serialize(ItemIdMapper itemIdMapper) {
        StringBuilder builder = new StringBuilder();
        builder.append('(').append(name);

        serializeProperties(builder, itemIdMapper);
        serializeChildren(builder, itemIdMapper);

        builder.append(')');
        return builder.toString();
    }

    private void serializeProperties(StringBuilder builder, ItemIdMapper itemIdMapper) {
        if (properties.isEmpty())
            return;

        builder.append(' ').append(Serializer.serializeMap(properties, itemIdMapper));
    }


    private void serializeChildren(StringBuilder builder, ItemIdMapper itemIdMapper) {
        for (Object child : children) {
            builder.append(' ').append(Serializer.serialize(child, itemIdMapper));
        }
    }

    public void setProperty(Object key, Object value) {
        properties.put(key, value);
    }
}
