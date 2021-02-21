// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

/**
 * A helper class to wrap a set of QRS cache settings.
 *
 * @author Steinar Knutsen
 */
public class QrsCache {

    public final Integer size;

    public QrsCache(Integer size) {
        this.size = size;
    }

}
