// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This class represents a path into a document, that can be used to iterate through the document and extract the field
 * values you're interested in.
 *
 * @author Thomas Gundersen
 */
public class FieldPath implements Iterable<FieldPathEntry> {

    private final List<FieldPathEntry> list;
    /**
     * Constructs an empty path.
     */
    public FieldPath() {
        list = Collections.emptyList();
    }

    /**
     * Constructs a path containing the entries of the specified path, in the order they are returned by that path's
     * iterator.
     *
     * @param path The path whose entries are to be placed into this path.
     * @throws NullPointerException If the specified path is null.
     */
    public FieldPath(FieldPath path) {
        this(path.list);
    }

    public FieldPath(List<FieldPathEntry> path) {
        list = Collections.unmodifiableList(path);
    }

    public int size() { return list.size(); }
    public FieldPathEntry get(int index) { return list.get(index); }
    public boolean isEmpty() { return list.isEmpty(); }
    public Iterator<FieldPathEntry> iterator() { return list.iterator(); }
    public List<FieldPathEntry> getList() { return list; }

    /**
     * Compares this field path with the given field path, returns true if the field path starts with the other.
     *
     * @param other The field path to compare with.
     * @return Returns true if this field path starts with the other field path, otherwise false
     */
    public boolean startsWith(FieldPath other) {
        if (other.size() > size()) {
            return false;
        }

        for (int i = 0; i < other.size(); i++) {
            if (!other.get(i).equals(get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return Returns the datatype we can expect this field path to return.
     */
    public DataType getResultingDataType() {
        if (isEmpty()) {
            return null;
        }

        return get(size() - 1).getResultingDataType();
    }

    /**
     * Convenience method to build a field path from a path string. This is a simple proxy for {@link
     * DataType#buildFieldPath(String)}.
     *
     * @param fieldType The data type of the value to build a path for.
     * @param fieldPath The path string to parse.
     * @return The corresponding field path object.
     */
    public static FieldPath newInstance(DataType fieldType, String fieldPath) {
        return fieldType.buildFieldPath(fieldPath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FieldPath that = (FieldPath) o;

        return list.equals(that.list);
    }

    @Override
    public int hashCode() {
        return list.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        DataType prevType = null;
        for (FieldPathEntry entry : this) {
            FieldPathEntry.Type type = entry.getType();
            switch (type) {
            case STRUCT_FIELD:
                if (out.length() > 0) {
                    out.append(".");
                }
                Field field = entry.getFieldRef();
                out.append(field.getName());
                prevType = field.getDataType();
                break;
            case ARRAY_INDEX:
                out.append("[").append(entry.getLookupIndex()).append("]");
                break;
            case MAP_KEY:
                out.append("{").append(entry.getLookupKey()).append("}");
                break;
            case MAP_ALL_KEYS:
                out.append(".key");
                break;
            case MAP_ALL_VALUES:
                out.append(".value");
                break;
            case VARIABLE:
                if (prevType instanceof ArrayDataType) {
                    out.append("[$").append(entry.getVariableName()).append("]");
                } else if (prevType instanceof WeightedSetDataType || prevType instanceof MapDataType) {
                    out.append("{$").append(entry.getVariableName()).append("}");
                } else {
                    out.append("$").append(entry.getVariableName());
                }
            }
        }
        return out.toString();
    }
}
