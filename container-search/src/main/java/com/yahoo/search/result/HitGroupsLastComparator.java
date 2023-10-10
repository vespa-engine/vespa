// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import java.util.Comparator;

/**
 * Ensures that HitGroups are placed last in the result.
 *
 * @author Tony Vaagenes
 */
public class HitGroupsLastComparator extends ChainableComparator {

    public HitGroupsLastComparator(Comparator<Hit> secondaryComparator) {
        super(secondaryComparator);
    }

    @Override
    public int compare(Hit left, Hit right) {
        if (isHitGroup(left) ^ isHitGroup(right)) {
            return isHitGroup(left) ? 1 : -1;
        } else {
            return super.compare(left, right);
        }
    }

    private boolean isHitGroup(Hit hit) {
        return hit instanceof HitGroup;
    }

    @Override
    public String toString() {
        return getSecondaryComparator().toString();
    }
}
