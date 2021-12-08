// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <iosfwd>

namespace proton {

/*
 * Class representing allocation strategy for large data structures
 * owned by a document sub db (e.g. attribute vectors, document meta store).
 */
class AllocStrategy
{
public:
    using CompactionStrategy = vespalib::datastore::CompactionStrategy;
protected:
    const search::GrowStrategy       _grow_strategy;
    const CompactionStrategy         _compaction_strategy;
    const uint32_t                   _amortize_count;

public:
    AllocStrategy(const search::GrowStrategy& grow_strategy,
                  const CompactionStrategy& compaction_strategy,
                  uint32_t amortize_count);

    AllocStrategy();
    ~AllocStrategy();

    bool operator==(const AllocStrategy &rhs) const noexcept;
    bool operator!=(const AllocStrategy &rhs) const noexcept {
        return !operator==(rhs);
    }
    const search::GrowStrategy& get_grow_strategy() const noexcept { return _grow_strategy; }
    const CompactionStrategy& get_compaction_strategy() const noexcept { return _compaction_strategy; }
    uint32_t get_amortize_count() const noexcept { return _amortize_count; }
};

std::ostream& operator<<(std::ostream& os, const AllocStrategy&alloc_strategy);

}
