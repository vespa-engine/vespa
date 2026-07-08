// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "index_searchable_stats.h"

#include <vespa/searchlib/common/create_and_freeze_times.h>

#include <string>

namespace searchcorespi {
namespace index {

struct IDiskIndex;

/**
 * Information about a disk index usable by state explorer.
 */
class DiskIndexStats : public IndexSearchableStats {
    std::string                          _indexDir;
    search::common::CreateAndFreezeTimes _create_and_freeze_times;

public:
    DiskIndexStats();
    DiskIndexStats(const IDiskIndex& index);
    ~DiskIndexStats();

    const std::string& getIndexdir() const { return _indexDir; }
    [[nodiscard]] const search::common::CreateAndFreezeTimes& create_and_freeze_times() const noexcept {
        return _create_and_freeze_times;
    }
};

} // namespace index
} // namespace searchcorespi
