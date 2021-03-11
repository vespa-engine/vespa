// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"

namespace vespalib::datastore {

/**
 * Less-than comparator for two entries based on entry refs.
 *
 * Valid entry ref is mapped to an entry in a data store.
 * Invalid entry ref is mapped to a temporary entry owned or referenced by comparator instance.
 */
class EntryComparator {
public:
    virtual ~EntryComparator() {}

    /**
     * Returns true if the value represented by lhs ref is less than the value represented by rhs ref.
     */
    virtual bool less(const EntryRef lhs, const EntryRef rhs) const = 0;
    virtual bool equal(const EntryRef lhs, const EntryRef rhs) const = 0;
    virtual size_t hash(const EntryRef rhs) const = 0;
};

}
