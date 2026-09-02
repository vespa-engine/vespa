// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.document;

import com.yahoo.text.Text;

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
     * Returns the separator used between the key and value in each synthetic
     * fast map search attribute value.
     */
    public static String keyValueSeparator() {
        return KEY_VALUE_SEPARATOR;
    }

    /**
     * The synthetic attribute is named &lt;field&gt;$keyvalue. The dollar sign makes the name
     * an illegal identifier, preventing it from being declared as a schema field, while still
     * allowing it to be referenced as a quoted attribute name.
     */
    public static String toKeyValueFieldName(String fieldName) {
        return fieldName + "$keyvalue";
    }

    /**
     * Combine key and value with separator. Expecting key and value to already be encoded.
     */
    public static String toKeyValueTerm(String encodedKey, String encodedValue) {
        return encodedKey + keyValueSeparator() + encodedValue;
    }

    /**
     * Combine key as string and value as int with separator.
     */
    public static String toKeyValue8Term(String encodedKey, int value) {
        return toKeyValueTerm(encodedKey, Text.toExcessHex8(value));
    }

    /**
     * Combine key as string and value as long with separator.
     */
    public static String toKeyValue16Term(String encodedKey, long value) {
        return toKeyValueTerm(encodedKey, Text.toExcessHex16(value));
    }

}
