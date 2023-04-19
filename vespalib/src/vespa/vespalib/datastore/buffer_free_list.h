// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"
#include "buffer_type.h"
#include <vespa/vespalib/util/array.h>

namespace vespalib::datastore {

class FreeList;

/**
 * Class containing the free list for a single buffer.
 *
 * The free list is a stack of EntryRef's that can be reused.
 */
class BufferFreeList {
private:
    using EntryRefArray = vespalib::Array<EntryRef>;

    std::atomic<EntryCount>& _dead_entries;
    FreeList* _free_list;
    EntryRefArray _free_refs;

    void attach();
    void detach();

public:
    BufferFreeList(std::atomic<EntryCount>& dead_entrie);
    ~BufferFreeList();
    BufferFreeList(BufferFreeList&&) = default; // Needed for emplace_back() during setup.
    BufferFreeList(const BufferFreeList&) = delete;
    BufferFreeList& operator=(const BufferFreeList&) = delete;
    BufferFreeList& operator=(BufferFreeList&&) = delete;
    void enable(FreeList& free_list);
    void disable();

    bool enabled() const { return _free_list != nullptr; }
    bool empty() const { return _free_refs.empty(); }
    void push_entry(EntryRef ref);
    EntryRef pop_entry();
};

}
