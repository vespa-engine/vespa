// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buffer_free_list.h"
#include "free_list.h"
#include <cassert>

namespace vespalib::datastore {

void
BufferFreeList::attach()
{
    assert(_free_list != nullptr);
    _free_list->attach(*this);
}

void
BufferFreeList::detach()
{
    assert(_free_list != nullptr);
    _free_list->detach(*this);
}

BufferFreeList::BufferFreeList(std::atomic<EntryCount>& dead_entries)
    : _dead_entries(dead_entries),
      _free_list(),
      _free_refs()
{
}

BufferFreeList::~BufferFreeList()
{
    assert(_free_list == nullptr);
    assert(_free_refs.empty());
}

void
BufferFreeList::enable(FreeList& free_list)
{
    assert(_free_list == nullptr);
    assert(_free_refs.empty());
    _free_list = &free_list;
}

void
BufferFreeList::disable()
{
    if (!empty()) {
        detach();
        EntryRefArray().swap(_free_refs);
    }
    _free_list = nullptr;
}

void
BufferFreeList::push_entry(EntryRef ref) {
    if (empty()) {
        attach();
    }
    _free_refs.push_back(ref);
}
EntryRef
BufferFreeList::pop_entry() {
    EntryRef ret = _free_refs.back();
    _free_refs.pop_back();
    if (empty()) {
        detach();
    }
    _dead_entries.store(_dead_entries.load(std::memory_order_relaxed) - 1, std::memory_order_relaxed);
    return ret;
}

}

