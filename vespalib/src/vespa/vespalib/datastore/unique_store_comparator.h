// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entry_comparator.h"
#include "unique_store_entry.h"
#include "datastore.h"

namespace search::datastore {

/*
 * Compare two entries based on entry refs.  Valid entry ref is mapped
 * to an entry in a data store.  Invalid entry ref is mapped to a
 * temporary entry referenced by comparator instance.
 */
template <typename EntryT, typename RefT>
class UniqueStoreComparator : public EntryComparator {
    using EntryType = EntryT;
    using WrappedEntryType = UniqueStoreEntry<EntryType>;
    using RefType = RefT;
    using DataStoreType = DataStoreT<RefT>;
    const DataStoreType &_store;
    const EntryType &_value;
public:
    UniqueStoreComparator(const DataStoreType &store, const EntryType &value)
        : _store(store),
          _value(value)
    {
    }
    inline const EntryType &get(EntryRef ref) const {
        if (ref.valid()) {
            RefType iRef(ref);
            return _store.template getEntry<WrappedEntryType>(iRef)->value();
        } else {
            return _value;
        }
    }
    bool operator()(const EntryRef lhs, const EntryRef rhs) const override
    {
        const EntryType &lhsValue = get(lhs);
        const EntryType &rhsValue = get(rhs);
        return lhsValue < rhsValue;
    }
};

}
