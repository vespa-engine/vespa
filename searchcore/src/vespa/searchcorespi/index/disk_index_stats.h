// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "index_searchable_stats.h"
#include <string>

namespace searchcorespi {
namespace index {

struct IDiskIndex;

/**
 * Information about a disk index usable by state explorer.
 */
class DiskIndexStats : public IndexSearchableStats {
    std::string _indexDir;
public:
    DiskIndexStats();
    DiskIndexStats(const IDiskIndex &index);
    ~DiskIndexStats();

    const std::string &getIndexdir() const { return _indexDir; }
};

} // namespace searchcorespi::index
} // namespace searchcorespi
