// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <functional>

namespace vespalib::datastore {

class EntryComparator;
struct ICompactable;
class UniqueStoreAddResult;

/**
 * Interface class for unique store dictionary.
 */
class IUniqueStoreDictionary {
public:
    /**
     * Class that provides a read snapshot of the dictionary.
     *
     * A generation guard that must be taken and held while the snapshot is considered valid.
     */
    class ReadSnapshot {
    public:
        using UP = std::unique_ptr<ReadSnapshot>;
        virtual ~ReadSnapshot() = default;
        virtual size_t count(const EntryComparator& comp) const = 0;
        virtual size_t count_in_range(const EntryComparator& low, const EntryComparator& high) const = 0;
        virtual void foreach_key(std::function<void(EntryRef)> callback) const = 0;
    };

    using generation_t = vespalib::GenerationHandler::generation_t;
    virtual ~IUniqueStoreDictionary() = default;
    virtual void freeze() = 0;
    virtual void transfer_hold_lists(generation_t generation) = 0;
    virtual void trim_hold_lists(generation_t firstUsed) = 0;
    virtual UniqueStoreAddResult add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry) = 0;
    virtual EntryRef find(const EntryComparator& comp) = 0;
    virtual void remove(const EntryComparator& comp, EntryRef ref) = 0;
    virtual void move_entries(ICompactable& compactable) = 0;
    virtual uint32_t get_num_uniques() const = 0;
    virtual vespalib::MemoryUsage get_memory_usage() const = 0;
    virtual void build(vespalib::ConstArrayRef<EntryRef>, vespalib::ConstArrayRef<uint32_t> ref_counts, std::function<void(EntryRef)> hold) = 0;
    virtual void build(vespalib::ConstArrayRef<EntryRef> refs) = 0;
    virtual void build_with_payload(vespalib::ConstArrayRef<EntryRef> refs, vespalib::ConstArrayRef<uint32_t> payloads) = 0;
    virtual std::unique_ptr<ReadSnapshot> get_read_snapshot() const = 0;
    virtual bool get_has_unordered_dictionary() const = 0;
};

}
