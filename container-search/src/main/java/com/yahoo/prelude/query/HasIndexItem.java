// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * An interface for items where it is useful to access an index name.
 *
 * @author Steinar Knutsen
 */
public interface HasIndexItem {

    String getIndexName();

    /**
     * Returns the field name searched by this item.
     * This is the full name with parent field name prepended by a dot if this is a child of a SameElement item,
     * and just the index name otherwise.
     * This should be used instead of getIndexName() when looking up field settings in index info.
     */
    default String getFieldName() { return getIndexName(); }

    /** Returns how many phrase words this item contains. */
    int getNumWords();

}
