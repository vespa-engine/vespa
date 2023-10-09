// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import java.util.Comparator;

/**
 * Ensures that meta hits are sorted before normal hits. All meta hits are
 * considered equal.
 *
 * @author Tony Vaagenes
 */
public class MetaHitsFirstComparator extends ChainableComparator {

    public MetaHitsFirstComparator(Comparator<Hit> secondaryComparator) {
        super(secondaryComparator);
    }

    @Override
    public int compare(Hit left, Hit right) {
        if (left.isMeta() && right.isMeta()) {
            return 0;
        } else if (left.isMeta()) {
            return -1;
        } else if (right.isMeta()) {
            return 1;
        } else {
            return super.compare(left, right);
        }
    }

    @Override
    public String toString() {
        return getSecondaryComparator().toString();
    }
}
