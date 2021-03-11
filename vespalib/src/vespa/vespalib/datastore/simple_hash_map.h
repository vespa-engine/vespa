// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_entry_ref.h"
#include <atomic>
#include <vespa/vespalib/util/generationholder.h>
#include <functional>

namespace vespalib::datastore {

class FixedSizeHashMap;
class EntryComparator;

/*
 * Hash map over keys in data store, meant to support a faster
 * dictionary for unique store with relation to lookups.
 *
 * Currently hardcoded key and data types, where key references an entry
 * in a UniqueStore and value references a posting list
 * (cf. search::attribute::PostingStore).
 *
 * This structure supports one writer and many readers.
 *
 * A reader must own an appropriate GenerationHandler::Guard to ensure
 * that memory is held while it can be accessed by reader.
 *
 * The writer must update generation and call transfer_hold_lists and
 * trim_hold_lists as needed to free up memory no longer needed by any
 * readers.
 */
class SimpleHashMap {
public:
    using KvType = std::pair<AtomicEntryRef, AtomicEntryRef>;
    using generation_t = GenerationHandler::generation_t;
    using sgeneration_t = GenerationHandler::sgeneration_t;
private:
    GenerationHolder _gen_holder;
    static constexpr size_t num_stripes = 1;
    std::atomic<FixedSizeHashMap *> _maps[num_stripes];
    std::unique_ptr<const EntryComparator> _comp;

    size_t get_stripe(const EntryComparator& comp, EntryRef key_ref) const;
    void alloc_stripe(size_t stripe);
    void hold_stripe(std::unique_ptr<const FixedSizeHashMap> map);
public:
    SimpleHashMap(std::unique_ptr<const EntryComparator> comp);
    ~SimpleHashMap();
    KvType& add(const EntryComparator& comp, std::function<EntryRef(void)> &insert_entry);
    KvType* remove(const EntryComparator& comp, EntryRef key_ref);
    const KvType* find(const EntryComparator& comp, EntryRef key_ref) const;
    void transfer_hold_lists(generation_t generation);
    void trim_hold_lists(generation_t first_used);
    size_t size() const noexcept;
};

}
