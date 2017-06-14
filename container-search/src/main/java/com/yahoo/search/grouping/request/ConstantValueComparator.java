// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Comparator;

/**
 * This class compares two constant values, and takes into account that one of
 * the arguments may be the very special infinity value.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
@SuppressWarnings("rawtypes")
public class ConstantValueComparator implements Comparator<ConstantValue> {
    @SuppressWarnings("unchecked")
    @Override
    public int compare(ConstantValue lhs, ConstantValue rhs) {
        // Run infinite comparison method if one of the arguments are infinite.
        if (rhs instanceof InfiniteValue) {
            return (-1 * rhs.getValue().compareTo(lhs));
        }
        return (lhs.getValue().compareTo(rhs.getValue()));
    }

}
