// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_free_list.h"
#include "entryref.h"
#include <vector>

namespace vespalib::datastore {

/**
 * Class containing the free list for a single buffer type id.
 *
 * This consists of a stack of buffer free lists,
 * where the newest attached is used when getting an EntryRef for reuse.
 */
class FreeList {
private:
    std::vector<BufferFreeList*> _free_lists;

public:
    FreeList();
    ~FreeList();
    FreeList(FreeList&&) = default; // Needed for emplace_back() during setup.
    FreeList(const FreeList&) = delete;
    FreeList& operator=(const FreeList&) = delete;
    FreeList& operator=(BufferFreeList&&) = delete;
    void attach(BufferFreeList& buf_list);
    void detach(BufferFreeList& buf_list);

    bool empty() const { return _free_lists.empty(); }
    size_t size() const { return _free_lists.size(); }
    uint32_t array_size() const { return _free_lists.back()->array_size(); }
    EntryRef pop_entry() {
        return _free_lists.back()->pop_entry();
    }
};

}
