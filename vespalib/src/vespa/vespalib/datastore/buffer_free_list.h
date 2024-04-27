// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"
#include "buffer_type.h"

namespace vespalib::datastore {

class FreeList;

/**
 * Class containing the free list for a single buffer.
 *
 * The free list is a stack of EntryRef's that can be reused.
 */
class BufferFreeList {
private:
    using EntryRefArray = std::vector<EntryRef>;

    std::atomic<EntryCount>& _dead_entries;
    FreeList                *_free_list;
    EntryRefArray            _free_refs;

    void attach();
    void detach() noexcept;

public:
    explicit BufferFreeList(std::atomic<EntryCount>& dead_entries) noexcept;
    ~BufferFreeList();
    BufferFreeList(BufferFreeList&&) noexcept = default; // Needed for emplace_back() during setup.
    BufferFreeList(const BufferFreeList&) = delete;
    BufferFreeList& operator=(const BufferFreeList&) = delete;
    BufferFreeList& operator=(BufferFreeList&&) = delete;
    void enable(FreeList& free_list) noexcept;
    void disable() noexcept;

    bool enabled() const noexcept { return _free_list != nullptr; }
    bool empty() const noexcept { return _free_refs.empty(); }
    void push_entry(EntryRef ref);
    EntryRef pop_entry() noexcept;
};

}
