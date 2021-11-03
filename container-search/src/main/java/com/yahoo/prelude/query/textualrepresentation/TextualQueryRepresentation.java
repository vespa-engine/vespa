// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.textualrepresentation;

import com.yahoo.prelude.query.Item;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Creates a detailed representation of a query tree.
 *
 * @author Tony Vaagenes
 */
public class TextualQueryRepresentation {

    private final Map<Item, Integer> itemReferences = new IdentityHashMap<>();
    private int nextItemReference = 0;

    final private ItemDiscloser rootDiscloser;

    @SuppressWarnings("rawtypes")
    private String valueString(Object value) {
        if (value == null)
            return null;
        else if (value instanceof String)
            return '"' + quote((String)value) + '"';
        else if (value instanceof Number || value instanceof Boolean || value instanceof Enum)
            return value.toString();
        else if (value instanceof Item)
            return itemReference((Item)value);
        else if (value.getClass().isArray())
            return listString(arrayToList(value).iterator());
        else if ( value instanceof List )
            return listString(((List)value).iterator());
        else if ( value instanceof Set )
            return listString( ((Set)value).iterator());
        else if ( value instanceof Map )
            return mapString((Map)value);
        else
            return '"' + quote(value.toString()) + '"';
    }

    //handles both primitive and object arrays.
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List arrayToList(Object array) {
        int length = Array.getLength(array);
        List list = new ArrayList();
        for (int i = 0; i<length; ++i)
            list.add(Array.get(array, i));
        return list;
    }

    private String mapString(Map<?, ?> map) {
        StringBuilder result = new StringBuilder();
        final String mapBegin = "map(";
        result.append(mapBegin);

        boolean firstTime = true;
        for (Map.Entry<?,?> entry: map.entrySet()) {
            if (!firstTime)
                result.append(' ');
            firstTime = false;

            result.append(valueString(entry.getKey())).append("=>").append(valueString(entry.getValue()));
        }

        result.append(')');
        return result.toString();
    }

    private String listString(Iterator<?> iterator) {
        StringBuilder result = new StringBuilder();
        result.append('(');

        boolean firstTime = true;
        while (iterator.hasNext()) {
            if (!firstTime)
                result.append(' ');
            firstTime = false;

            result.append(valueString(iterator.next()));
        }

        result.append(')');
        return result.toString();
    }

    private String itemReference(Item item) {
        Integer reference = itemReferences.get(item);
        return reference != null ? reference.toString() : "Unknown item: '"  + System.identityHashCode(item) + "'";
    }

    private static String quote(String s) {
        return s.replaceAll("\"", "\\\\\"" );
    }

    private ItemDiscloser expose(Item item) {
        ItemDiscloser itemDiscloser = new ItemDiscloser(item);
        item.disclose(itemDiscloser);
        return itemDiscloser;
    }

    public TextualQueryRepresentation(Item root) {
        rootDiscloser = expose(root);
    }

    @Override
    public String toString() {
        return rootDiscloser.toString();
    }

    /** Creates the textual representation for a single Item. */
    private class ItemDiscloser implements Discloser {

        private final Item item;

        final Map<String, Object> properties = new TreeMap<>();
        final String name;

        Object value;
        final List<ItemDiscloser> children = new ArrayList<>();

        ItemDiscloser(Item item) {
            this.item = item;
            name = item.getName();
        }

        public void addProperty(String key, Object value) {
            assert(key.indexOf(' ') == -1);
            properties.put(key, value);

            if (value instanceof Item)
                setItemReference((Item)value);
        }

        public void setValue(Object value) {
            assert(children.isEmpty());
            this.value = value;
        }

        public void addChild(Item child) {
            assert(value == null);
            children.add(expose(child));
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(name);

            if ( ! properties.isEmpty() || itemReferences.get(item) != null) {
                builder.append('[');
                addPropertiesString(builder);
                builder.append(']');
            }

            if (value != null || !children.isEmpty()) {
                builder.append("{\n");
                addBody(builder);
                builder.append("}\n");
            }
            return builder.toString();
        }

        private void addBody(StringBuilder builder) {
            if (value != null) {
                addIndented(builder, valueString(value));
            } else {
                for (ItemDiscloser child : children) {
                    addIndented(builder, child.toString());
                }
            }
        }

        //for each line: add "<indentation><line><newline>"
        private void addIndented(StringBuilder builder, String toAdd) {
            String indent = "  ";
            for (String line : toAdd.split(Pattern.quote("\n")))
                builder.append(indent).append(line).append('\n');
        }

        private void addPropertiesString(StringBuilder s) {
            boolean firstTime = true;

            Integer itemReference = itemReferences.get(item);
            if (itemReference != null) {
                addPropertyString(s, "%id", itemReference);
                firstTime = false;
            }

            for (Map.Entry<String,Object> entry : properties.entrySet()) {
                if (!firstTime) {
                    s.append(' ');
                }
                addPropertyString(s, entry.getKey(), entry.getValue());
                firstTime = false;
            }
        }

        private void addPropertyString(StringBuilder s, String key, Object value) {
            s.append(key).append('=').append(valueString(value));
        }

        private void setItemReference(Item item) {
            if (itemReferences.get(item) == null)
                itemReferences.put(item, nextItemReference++);
        }

    }

}
