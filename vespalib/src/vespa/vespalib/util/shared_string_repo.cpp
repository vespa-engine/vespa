// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_string_repo.h"

namespace vespalib {

SharedStringRepo::Partition::~Partition() = default;

void
SharedStringRepo::Partition::find_leaked_entries(size_t my_idx) const
{
    for (size_t i = 0; i < _entries.size(); ++i) {
        if (!_entries[i].is_free()) {
            size_t id = (((i << PART_BITS) | my_idx) + 1);
            fprintf(stderr, "WARNING: shared_string_repo: leaked string id: %zu ('%s')\n",
                    id, _entries[i].str().c_str());
        }
    }
}

void
SharedStringRepo::Partition::make_entries(size_t hint)
{
    hint = std::max(hint, _entries.size() + 1);
    size_t want_mem = roundUp2inN(hint * sizeof(Entry));
    size_t want_entries = want_mem / sizeof(Entry);   
    _entries.reserve(want_entries);
    while (_entries.size() < _entries.capacity()) {
        _entries.emplace_back(_free);
        _free = (_entries.size() - 1);
    }
}

SharedStringRepo::SharedStringRepo() = default;
SharedStringRepo::~SharedStringRepo()
{
    for (size_t p = 0; p < _partitions.size(); ++p) {
        _partitions[p].find_leaked_entries(p);
    }
}

SharedStringRepo &
SharedStringRepo::get()
{
    static SharedStringRepo repo;
    return repo;
}

SharedStringRepo::WeakHandles::WeakHandles(size_t expect_size)
    : _handles()
{
    _handles.reserve(expect_size);
}

SharedStringRepo::WeakHandles::~WeakHandles() = default;

SharedStringRepo::StrongHandles::StrongHandles(size_t expect_size)
    : _repo(SharedStringRepo::get()),
      _handles()
{
    _handles.reserve(expect_size);
}

SharedStringRepo::StrongHandles::StrongHandles(StrongHandles &&rhs)
    : _repo(rhs._repo),
      _handles(std::move(rhs._handles))
{
    assert(rhs._handles.empty());
}

SharedStringRepo::StrongHandles::~StrongHandles()
{
    for (uint32_t handle: _handles) {
        _repo.reclaim(handle);
    }
}

}
