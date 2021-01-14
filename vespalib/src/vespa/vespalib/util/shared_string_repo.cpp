// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_string_repo.h"

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.shared_string_repo");

namespace vespalib {

SharedStringRepo::Stats::Stats()
    : active_entries(0),
      total_entries(0),
      min_free(PART_LIMIT)
{
}

void
SharedStringRepo::Stats::merge(const Stats &s)
{
    active_entries += s.active_entries;
    total_entries += s.total_entries;
    min_free = std::min(min_free, s.min_free);
}

double
SharedStringRepo::Stats::id_space_usage() const
{
    return (1.0 - (double(min_free) / double(PART_LIMIT)));
}

SharedStringRepo::Partition::~Partition() = default;

void
SharedStringRepo::Partition::find_leaked_entries(size_t my_idx) const
{
    for (size_t i = 0; i < _entries.size(); ++i) {
        if (!_entries[i].is_free()) {
            size_t id = (((i << PART_BITS) | my_idx) + 1);
            LOG(warning, "leaked string id: %zu (part: %zu/%d, string: '%s')\n",
                id, my_idx, NUM_PARTS, _entries[i].str().c_str());
        }
    }
}

SharedStringRepo::Stats
SharedStringRepo::Partition::stats() const
{
    Stats stats;
    std::lock_guard guard(_lock);
    stats.active_entries = _hash.size();
    stats.total_entries = _entries.size();
    stats.min_free = (PART_LIMIT - _hash.size());
    return stats;
}

void
SharedStringRepo::Partition::make_entries(size_t hint)
{
    hint = std::max(hint, _entries.size() + 1);
    size_t want_mem = roundUp2inN(hint * sizeof(Entry));
    size_t want_entries = want_mem / sizeof(Entry);
    want_entries = std::min(want_entries, PART_LIMIT);
    assert(want_entries > _entries.size());
    _entries.reserve(want_entries);
    while (_entries.size() < _entries.capacity()) {
        _entries.emplace_back(_free);
        _free = (_entries.size() - 1);
    }
}

SharedStringRepo SharedStringRepo::_repo;

SharedStringRepo::SharedStringRepo() = default;
SharedStringRepo::~SharedStringRepo()
{
    for (size_t p = 0; p < _partitions.size(); ++p) {
        _partitions[p].find_leaked_entries(p);
    }
}

SharedStringRepo::Stats
SharedStringRepo::stats()
{
    Stats stats;
    for (const auto &part: _repo._partitions) {
        stats.merge(part.stats());
    }
    return stats;
}

SharedStringRepo::Handles::Handles()
    : _handles()
{
}

SharedStringRepo::Handles::Handles(Handles &&rhs)
    : _handles(std::move(rhs._handles))
{
    assert(rhs._handles.empty());
}

SharedStringRepo::Handles::~Handles()
{
    for (string_id handle: _handles) {
        _repo.reclaim(handle);
    }
}

}
