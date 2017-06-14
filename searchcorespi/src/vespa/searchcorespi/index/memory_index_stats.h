// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "index_searchable_stats.h"

namespace searchcorespi {
namespace index {

struct IMemoryIndex;

/**
 * Information about a memory index usable by state explorer.
 */
class MemoryIndexStats : public IndexSearchableStats {
public:
    MemoryIndexStats();
    MemoryIndexStats(const IMemoryIndex &index);
    ~MemoryIndexStats();
};

} // namespace searchcorespi::index
} // namespace searchcorespi
