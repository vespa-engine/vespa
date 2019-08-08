// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entry_comparator.h"
#include "datastore.h"

namespace search::datastore {

template <typename EntryT, typename RefT>
class UniqueStoreComparator : public EntryComparator {
    using EntryType = EntryT;
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
            return *_store.template getEntry<EntryType>(iRef);
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
