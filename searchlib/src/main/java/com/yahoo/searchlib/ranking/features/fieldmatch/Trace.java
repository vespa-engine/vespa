// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

/**
 * A computation trace
 *
 * @author bratseth
 */
public class Trace {

    private StringBuilder b = new StringBuilder();

    public void add(String s) {
        b.append(b);
    }

    @Override
    public String toString() {
        return b.toString();
    }

}
