// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_entry_ref.h"
#include <functional>

namespace vespalib::datastore {

class EntryComparator;

/**
 * Class that provides a read snapshot of the dictionary.
 *
 * A generation guard that must be taken and held while the snapshot is considered valid.
 */
class IUniqueStoreDictionaryReadSnapshot {
public:
    virtual ~IUniqueStoreDictionaryReadSnapshot() = default;
    virtual void fill() = 0;
    virtual void sort() = 0;
    virtual size_t count(const EntryComparator& comp) const = 0;
    virtual size_t count_in_range(const EntryComparator& low, const EntryComparator& high) const = 0;
    virtual void foreach_key(std::function<void(const AtomicEntryRef&)> callback) const = 0;
};

}
