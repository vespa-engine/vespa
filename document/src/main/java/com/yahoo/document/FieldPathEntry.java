// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;

/**
 * @author thomasg
 */
public class FieldPathEntry {
    public enum Type {
        STRUCT_FIELD,
        ARRAY_INDEX,
        MAP_KEY,
        MAP_ALL_KEYS,
        MAP_ALL_VALUES,
        VARIABLE
    }

    private final Type type;
    private final int lookupIndex;
    private final FieldValue lookupKey;
    private final String variableName;
    private final Field fieldRef;
    private final DataType resultingDataType;

    public DataType getResultingDataType() {
        return resultingDataType;
    }

    public Type getType() {
        return type;
    }

    public Field getFieldRef() {
        return fieldRef;
    }

    public int getLookupIndex() {
        return lookupIndex;
    }

    public FieldValue getLookupKey() {
        return lookupKey;
    }

    public String getVariableName() {
        return variableName;
    }

    public String toString() {
        String retVal = type.toString() + ": ";
        switch (type) {
            case STRUCT_FIELD:
                retVal += getFieldRef();
                break;
            case ARRAY_INDEX:
                retVal += getLookupIndex();
                break;
            case MAP_KEY:
                retVal += getLookupKey();
                break;
            case MAP_ALL_KEYS:
            case MAP_ALL_VALUES:
                break;
            case VARIABLE:
                retVal += getVariableName();
                break;
        }
        return retVal;
    }

    /**
     * Creates a new field path entry that references a struct field.
     * For these kinds of field path entries, getFieldRef() is valid.
     *
     * @param fieldRef The field to look up in the struct.
     * @return The new field path entry
     */
    public static FieldPathEntry newStructFieldEntry(Field fieldRef) {
        return new FieldPathEntry(fieldRef);
    }

    /**
     * Creates a new field path entry that references an array index.
     *
     * @param lookupIndex The index to look up
     * @param resultingDataType The datatype of the contents of the array
     * @return The new field path entry
     */
    public static FieldPathEntry newArrayLookupEntry(int lookupIndex, DataType resultingDataType) {
        return new FieldPathEntry(lookupIndex, resultingDataType);
    }

    /**
     * Creates a new field path entry that references a map or weighted set.
     *
     * @param lookupKey The value of the key in the map or weighted set to recurse into.
     * @param resultingDataType The datatype of values in the map or weighted set.
     * @return The new field path entry
     */
    public static FieldPathEntry newMapLookupEntry(FieldValue lookupKey, DataType resultingDataType) {
        return new FieldPathEntry(lookupKey, resultingDataType);
    }

    /**
     * Creates a new field path entry that digs through all the keys of a map or weighted set.
     *
     * @param resultingDataType The datatype of the keys in the map or weighted set.
     * @return The new field path entry.
     */
    public static FieldPathEntry newAllKeysLookupEntry(DataType resultingDataType) {
        return new FieldPathEntry(true, false, resultingDataType);
    }

    /**
     * Creates a new field path entry that digs through all the values of a map or weighted set.
     *
     * @param resultingDataType The datatype of the values in the map or weighted set.
     * @return The new field path entry.
     */
    public static FieldPathEntry newAllValuesLookupEntry(DataType resultingDataType) {
        return new FieldPathEntry(false, true, resultingDataType);
    }

    /**
     * Creates a new field path entry that digs through all the keys in a map or weighted set, or all the indexes of an array,
     * an sets the given variable name as it does so (or, if the variable is set, uses the set variable to look up the
     * collection.
     *
     * @param variableName The name of the variable to lookup in the collection
     * @param resultingDataType The value type of the collection we're digging through
     * @return The new field path entry.
     */
    public static FieldPathEntry newVariableLookupEntry(String variableName, DataType resultingDataType) {
        return new FieldPathEntry(variableName, resultingDataType);
    }

    private FieldPathEntry(Field fieldRef) {
        type = Type.STRUCT_FIELD;
        lookupIndex = 0;
        lookupKey = null;
        variableName = null;
        this.fieldRef = fieldRef;
        resultingDataType = fieldRef.getDataType();
    }

    private FieldPathEntry(int lookupIndex, DataType resultingDataType) {
        type = Type.ARRAY_INDEX;
        this.lookupIndex = lookupIndex;
        lookupKey = null;
        variableName = null;
        fieldRef = null;
        this.resultingDataType = resultingDataType;
    }

    private FieldPathEntry(FieldValue lookupKey, DataType resultingDataType) {
        type = Type.MAP_KEY;
        lookupIndex = 0;
        this.lookupKey = lookupKey;
        variableName = null;
        fieldRef = null;
        this.resultingDataType = resultingDataType;
    }

    private FieldPathEntry(boolean keysOnly, boolean valuesOnly, DataType resultingDataType) {
        type = keysOnly ? Type.MAP_ALL_KEYS : Type.MAP_ALL_VALUES;
        lookupIndex = 0;
        lookupKey = null;
        variableName = null;
        fieldRef = null;
        this.resultingDataType = resultingDataType;
    }

    private FieldPathEntry(String variableName, DataType resultingDataType) {
        type = Type.VARIABLE;
        lookupIndex = 0;
        lookupKey = null;
        this.variableName = variableName;
        fieldRef = null;
        this.resultingDataType = resultingDataType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FieldPathEntry that = (FieldPathEntry) o;

        if (lookupIndex != that.lookupIndex) return false;
        if (fieldRef != null ? !fieldRef.equals(that.fieldRef) : that.fieldRef != null) return false;
        if (lookupKey != null ? !lookupKey.equals(that.lookupKey) : that.lookupKey != null) return false;
        if (resultingDataType != null ? !resultingDataType.equals(that.resultingDataType) : that.resultingDataType != null)
            return false;
        if (type != that.type) return false;
        if (variableName != null ? !variableName.equals(that.variableName) : that.variableName != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + lookupIndex;
        result = 31 * result + (lookupKey != null ? lookupKey.hashCode() : 0);
        result = 31 * result + (variableName != null ? variableName.hashCode() : 0);
        result = 31 * result + (fieldRef != null ? fieldRef.hashCode() : 0);
        result = 31 * result + (resultingDataType != null ? resultingDataType.hashCode() : 0);
        return result;
    }

    public static class KeyParseResult {
        public String parsed;
        public int consumedChars;

        public KeyParseResult(String parsed, int consumedChars) {
            this.parsed = parsed;
            this.consumedChars = consumedChars;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            KeyParseResult that = (KeyParseResult) o;

            if (consumedChars != that.consumedChars) return false;
            if (!parsed.equals(that.parsed)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = parsed.hashCode();
            result = 31 * result + consumedChars;
            return result;
        }

        @Override
        public String toString() {
            return "KeyParseResult(parsed=\"" + parsed + "\", consumedChars=" + consumedChars + ")";
        }
    }

    private static int parseQuotedString(String key, int offset,
                                          int len, StringBuilder builder)
    {
        for (; offset < len && key.charAt(offset) != '"'; ++offset) {
            if (key.charAt(offset) == '\\') {
                ++offset; // Skip escape backslash
                if (offset == len || key.charAt(offset) != '"') {
                    throw new IllegalArgumentException("Escaped key '" + key + "' has bad quote character escape sequence. Expected '\"'");
                }
            }
            if (offset < len) {
                builder.append(key.charAt(offset));
            }
        }
        if (offset < len && key.charAt(offset) == '"') {
            return offset + 1;
        } else {
            throw new IllegalArgumentException("Escaped key '" + key + "' is incomplete. No matching '\"'");
        }
    }

    private static int skipWhitespace(String str, int offset, int len) {
        while (offset < len && Character.isSpaceChar(str.charAt(offset))) {
            ++offset;
        }
        return offset;
    }

    /**
     * Parse a field path map key of the form {xyz} or {"xyz"} with optional trailing data.
     * If the key contains a '}' or '"' character, the key must be in quotes and all
     * double-quote characters must be escaped. Only '"' chars may be escaped. Any
     * trailing string data past the '}' is ignored.
     *
     * @param key Part of a field path that contains a key at its start
     * @return A parse result containing the parsed/unescaped key and the number
     *     of input characters the parse consumed. Does not include any characters
     *     beyond the '}' char.
     */
    public static KeyParseResult parseKey(String key) {
        StringBuilder parsed = new StringBuilder(key.length());
        // Hooray for ad-hoc parsing
        int len = key.length();
        int i = 0;
        if (i < len && key.charAt(0) == '{') {
            i = skipWhitespace(key, i + 1, len);
            if (i < len && key.charAt(i) == '"') {
                i = parseQuotedString(key, i + 1, len, parsed);
            } else {
                // No quoting, use all of string until '}' verbatim
                while (i < len && key.charAt(i) != '}') {
                    parsed.append(key.charAt(i));
                    ++i;
                }
            }
            i = skipWhitespace(key, i, len);
            if (i < len && key.charAt(i) == '}') {
                return new KeyParseResult(parsed.toString(), i + 1);
            } else {
                throw new IllegalArgumentException("Key '" + key + "' is incomplete. No matching '}'");
            }
        } else {
            throw new IllegalArgumentException("Key '" + key + "' does not start with '{'");
        }
    }
}
