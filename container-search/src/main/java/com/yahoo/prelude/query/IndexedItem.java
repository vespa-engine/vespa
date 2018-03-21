// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;


/**
 * Interface for Items that is indexed
 *
 * @author Lars Christian Jensen
 */
public interface IndexedItem extends HasIndexItem {

    /** Sets the name of the index to search */
    void setIndexName(String index);

    /**
     * Return the searchable term contents of this item.
     *
     * @return a string representation of what is presumably stored in an index
     *         which will match this item
     */
    String getIndexedString();

}
