// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "free_list.h"
#include <algorithm>
#include <cassert>

namespace vespalib::datastore {

FreeList::FreeList()
    : _free_lists()
{
}

FreeList::~FreeList()
{
    assert(_free_lists.empty());
}

void
FreeList::attach(BufferFreeList& buf_list)
{
    _free_lists.push_back(&buf_list);
}

void
FreeList::detach(BufferFreeList& buf_list)
{
    if (!_free_lists.empty() && (_free_lists.back() == &buf_list)) {
        _free_lists.pop_back();
        return;
    }
    auto itr = std::find(_free_lists.begin(), _free_lists.end(), &buf_list);
    assert(itr != _free_lists.end());
    _free_lists.erase(itr);
}

}
