// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/generationhandler.h>
#include "entryref.h"
#include <functional>

namespace search::datastore {

class EntryComparator;
class ICompactable;
class UniqueStoreAddResult;

/*
 * Interface class for unique store dictonary.
 */
class UniqueStoreDictionaryBase
{
public:
    using generation_t = vespalib::GenerationHandler::generation_t;
    virtual ~UniqueStoreDictionaryBase() = default;
    virtual void freeze() = 0;
    virtual void transfer_hold_lists(generation_t generation) = 0;
    virtual void trim_hold_lists(generation_t firstUsed) = 0;
    virtual UniqueStoreAddResult add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry) = 0;
    virtual EntryRef find(const EntryComparator& comp) = 0;
    virtual bool remove(const EntryComparator& comp, EntryRef ref) = 0;
    virtual void move_entries(ICompactable& compactable) = 0;
    virtual uint32_t get_num_uniques() const = 0;
    virtual vespalib::MemoryUsage get_memory_usage() const = 0;
    virtual void build(const std::vector<EntryRef> &refs, const std::vector<uint32_t> &ref_counts, std::function<void(EntryRef)> hold) = 0;
    virtual EntryRef get_frozen_root() const = 0;
    virtual void foreach_key(EntryRef root, std::function<void(EntryRef)> callback) const = 0;
};

}
