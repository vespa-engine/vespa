// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * An interface for items where it is useful to access an index name.
 *
 * @author  Steinar Knutsen
 */
public interface HasIndexItem {

    String getIndexName();

    /** Returns how many phrase words does this item contain */
    int getNumWords();

}
