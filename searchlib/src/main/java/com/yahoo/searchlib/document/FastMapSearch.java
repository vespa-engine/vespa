// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.document;

/**
 * Fast map search is a feature that allows searching efficiently in a map by creating a
 * synthetic attribute where the elements are concatenation of key and value.
 *
 * @author johsol
 */
public class FastMapSearch {

    /** DEL cannot occur in a map key and is accepted by Text.isTextCharacter. */
    static final String KEY_VALUE_SEPARATOR = String.valueOf((char) 0x7F);

    /**
     * Returns the separator used to separate key and value in the synthetic attribute created for fast map search.
     */
    public static String keyValueSeparator() {
        return KEY_VALUE_SEPARATOR;
    }

    /**
     * The synthetic attribute is named &lt;field&gt;$keyvalue. The dollar sign makes the name
     * impossible to write in queries or declare in a schema while still being a legal identifier
     * in the indexing language.
     */
    public static String toKeyValueFieldName(String fieldName) {
        return fieldName + "$keyvalue";
    }

}
