// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_allocator.h"
#include <vespa/vespalib/stllike/allocator.h>

namespace vespalib::datastore {

class IUniqueStoreDictionary;

/**
 * Builder for related UniqueStore class.
 *
 * Contains utility method for adding new unique values and mapping
 * from enum value to EntryRef value.  New unique values must be added
 * in sorted order.
 */
template <typename Allocator>
class UniqueStoreBuilder {
    using EntryType = typename Allocator::EntryType;

    Allocator& _allocator;
    IUniqueStoreDictionary& _dict;
    std::vector<EntryRef, allocator_large<EntryRef>> _refs;
    std::vector<uint32_t, allocator_large<uint32_t>> _refCounts;

public:
    UniqueStoreBuilder(Allocator& allocator, IUniqueStoreDictionary& dict, uint32_t uniqueValuesHint);
    ~UniqueStoreBuilder();
    void setupRefCounts();
    void makeDictionary();
    void add(const EntryType& value) {
        EntryRef newRef = _allocator.allocate(value);
        _refs.push_back(newRef);
    }
    EntryRef mapEnumValueToEntryRef(uint32_t enumValue) {
        assert(enumValue < _refs.size());
        assert(_refCounts[enumValue] < std::numeric_limits<uint32_t>::max());
        ++_refCounts[enumValue];
        return _refs[enumValue];
    }
};

}
