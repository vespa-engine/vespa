// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"

namespace search::datastore {

/*
 * Compare two entries based on entry refs.  Valid entry ref is mapped
 * to an entry in a data store.  Invalid entry ref is mapped to a
 * temporary entry owned or referenced by comparator instance.
 */
class EntryComparator {
public:
    virtual ~EntryComparator() {}
    /**
     * Compare the values represented by the given unique store entry refs.
     **/
    virtual bool operator()(const EntryRef lhs, const EntryRef rhs) const = 0;
};

}
