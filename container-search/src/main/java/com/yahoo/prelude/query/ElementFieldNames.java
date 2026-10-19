// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * Resolves the full field name searched by an item, which is inside a parent field
 * if the item is a child of a sameElement or mapMatch.
 *
 * @author arnej
 */
class ElementFieldNames {

    private ElementFieldNames() { }

    /** Returns the given index name, prefixed by the enclosing element field, if any. */
    static String fieldName(CompositeItem parent, String indexName) {
        while (parent != null) {
            if (parent instanceof SameElementItem sameElement)
                return sameElement.getFieldName() + "." + indexName;
            if (parent instanceof MapMatchItem mapMatch)
                return mapMatch.getFieldName() + "." + indexName;
            parent = parent.getParent();
        }
        return indexName;
    }

}
