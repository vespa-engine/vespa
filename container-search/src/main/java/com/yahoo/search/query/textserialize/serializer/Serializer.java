// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.serializer;

import com.yahoo.prelude.query.Item;
import com.yahoo.search.query.textserialize.item.ItemExecutorRegistry;

import java.util.List;
import java.util.Map;

import static com.yahoo.search.query.textserialize.item.ListUtil.butFirst;
import static com.yahoo.search.query.textserialize.item.ListUtil.first;

/**
 * @author Tony Vaagenes
 */
class Serializer {

    static String serialize(Object child, ItemIdMapper itemIdMapper) {
        if (child instanceof DispatchForm) {
            return ((DispatchForm) child).serialize(itemIdMapper);
        } else if (child instanceof Item) {
            return serializeItem((Item) child, itemIdMapper);
        } else if (child instanceof String) {
            return serializeString((String) child);
        } else if (child instanceof Number) {
            return child.toString();
        } else if (child instanceof Map) {
            return serializeMap((Map<?, ?>)child, itemIdMapper);
        } else if (child instanceof List) {
            return serializeList((List<?>)child, itemIdMapper);
        } else {
            throw new IllegalArgumentException("Can't serialize type " + child.getClass());
        }
    }

    private static String serializeString(String string) {
        return '"' + string.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    static String serializeList(List<?> list, ItemIdMapper itemIdMapper) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');

        if (!list.isEmpty()) {
            builder.append(serialize(first(list), itemIdMapper));

            for (Object element : butFirst(list)) {
                builder.append(", ").append(serialize(element, itemIdMapper));
            }
        }

        builder.append(']');
        return builder.toString();
    }

    static String serializeMap(Map<?, ?> map, ItemIdMapper itemIdMapper) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");

        if (!map.isEmpty()) {
            serializeEntry(builder, first(map.entrySet()), itemIdMapper);
            for (Map.Entry<?, ?> entry : butFirst(map.entrySet())) {
                builder.append(", ");
                serializeEntry(builder, entry, itemIdMapper);
            }
        }

        builder.append('}');
        return builder.toString();
    }

    static void serializeEntry(StringBuilder builder, Map.Entry<?, ?> entry, ItemIdMapper itemIdMapper) {
        builder.append(serialize(entry.getKey(), itemIdMapper)).append(' ').
                append(serialize(entry.getValue(), itemIdMapper));
    }

    static String serializeItem(Item item, ItemIdMapper itemIdMapper) {
        return ItemExecutorRegistry.getByType(item.getItemType()).itemToForm(item, itemIdMapper).serialize(itemIdMapper);
    }

}
