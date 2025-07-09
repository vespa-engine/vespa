// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.text.Text;

import java.util.Objects;

/**
 * Represents visiting constrained over a document subset that shares the same
 * location affinity ('number' or 'group' value).
 *
 * @author Jon Marius Venstad
 */
record Group(String docIdPart, String selection) {

    public static Group of(long value) {
        String stringValue = Long.toUnsignedString(value);
        return new Group("n=" + stringValue, "id.user==" + stringValue);
    }

    public static Group of(String value) {
        Text.validateTextString(value)
                .ifPresent(codePoint -> {
                    throw new IllegalArgumentException(String.format("Illegal code point U%04X in group", codePoint));
                });

        return new Group("g=" + value, "id.group=='" + value.replaceAll("'", "\\\\'") + "'");
    }

}
