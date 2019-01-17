// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import edu.umd.cs.findbugs.annotations.NonNull;


/**
 * An interface for items where it is useful to access an associated
 * index name.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public interface HasIndexItem {

    @NonNull
    public String getIndexName();

    /** @return how many phrase words does this item contain */
    public int getNumWords();

}
