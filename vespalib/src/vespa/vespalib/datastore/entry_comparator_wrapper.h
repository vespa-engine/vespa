// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entry_comparator.h"

namespace vespalib::datastore {

/*
 * Copyable comparator wrapper.
 */
class EntryComparatorWrapper {
    const EntryComparator &_comp;
public:
    EntryComparatorWrapper(const EntryComparator &comp)
        : _comp(comp)
    { }
    bool operator()(const EntryRef &lhs, const EntryRef &rhs) const {
        return _comp.less(lhs, rhs);
    }
};

}
