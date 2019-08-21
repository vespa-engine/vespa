// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entry_comparator.h"
#include "unique_store_entry.h"
#include "datastore.h"
#include <cmath>

namespace search::datastore {

/*
 * Helper class for comparing elements in unique store. 
 */
template <typename EntryT>
class UniqueStoreComparatorHelper {
public:
    static bool less(const EntryT& lhs, const EntryT& rhs) {
        return lhs < rhs;
    }
};

/*
 * Helper class for comparing floating point elements in unique store with
 * special handling of NAN.
 */
template <typename EntryT>
class UniqueStoreFloatingPointComparatorHelper
{
public:
    static bool less(EntryT lhs, const EntryT rhs) {
        if (std::isnan(lhs)) {
            return !std::isnan(rhs);
        } else if (std::isnan(rhs)) {
            return false;
        } else {
            return (lhs < rhs);
        }
    }
};

/*
 * Specialized helper class for comparing float elements in unique store with
 * special handling of NAN.
 */
template <>
class UniqueStoreComparatorHelper<float> : public UniqueStoreFloatingPointComparatorHelper<float> {
};

/*
 * Specialized helper class for comparing double elements in unique store with
 * special handling of NAN.
 */
template <>
class UniqueStoreComparatorHelper<double> : public UniqueStoreFloatingPointComparatorHelper<double> {
};
  
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
        return UniqueStoreComparatorHelper<EntryT>::less(lhsValue, rhsValue);
    }
};

}
