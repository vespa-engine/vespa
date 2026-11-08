// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a decodefastmaplookup function in a {@link GroupingExpression}. It evaluates to the entries
 * of a map, or array of struct, with a fast-search-map-field lookup, as strings on the form key + separator + value.
 *
 * The lookup is named "fieldname.lookupname", and the separator is ":" unless given.
 *
 * @author arnej
 */
public class DecodeFastMapLookupFunction extends DocumentValue {

    public static final String DEFAULT_SEPARATOR = ":";

    private final String fieldName;
    private final String lookupName;
    private final String separator;

    /**
     * Constructs a new instance of this class, with the default separator.
     *
     * @param fieldName  the name of the map or array of struct field
     * @param lookupName the name of the fast-search-map-field lookup in that field
     */
    public DecodeFastMapLookupFunction(String fieldName, String lookupName) {
        this(fieldName, lookupName, DEFAULT_SEPARATOR);
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param fieldName  the name of the map or array of struct field
     * @param lookupName the name of the fast-search-map-field lookup in that field
     * @param separator  the separator between key and value in the decoded entries
     */
    public DecodeFastMapLookupFunction(String fieldName, String lookupName, String separator) {
        this(null, null, fieldName, lookupName, separator);
    }

    private DecodeFastMapLookupFunction(String label, Integer level, String fieldName, String lookupName, String separator) {
        super(toImage(fieldName, lookupName, separator), label, level);
        this.fieldName = fieldName;
        this.lookupName = lookupName;
        this.separator = separator;
    }

    private static String toImage(String fieldName, String lookupName, String separator) {
        if (fieldName == null || fieldName.isEmpty() || lookupName == null || lookupName.isEmpty())
            throw new IllegalArgumentException("decodefastmaplookup requires a field name and a lookup name");
        if (separator == null)
            throw new IllegalArgumentException("decodefastmaplookup separator cannot be null");
        String name = fieldName + "." + lookupName;
        return "decodefastmaplookup(" + name +
               (separator.equals(DEFAULT_SEPARATOR) ? "" : ", " + asImage(separator)) + ")";
    }

    @Override
    public DecodeFastMapLookupFunction copy() {
        return new DecodeFastMapLookupFunction(getLabel(), getLevelOrNull(), fieldName, lookupName, separator);
    }

    /** Returns the name of the map or array of struct field. */
    public String getFieldName() { return fieldName; }

    /** Returns the name of the fast-search-map-field lookup in the field. */
    public String getLookupName() { return lookupName; }

    /** Returns the name of this lookup as given to the backend, on the form "fieldname.lookupname". */
    public String getName() { return fieldName + "." + lookupName; }

    /** Returns the separator between key and value in the decoded entries. */
    public String getSeparator() { return separator; }

}
