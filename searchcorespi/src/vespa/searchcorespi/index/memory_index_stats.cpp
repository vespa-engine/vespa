// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_index_stats.h"
#include "imemoryindex.h"

namespace searchcorespi::index {

MemoryIndexStats::MemoryIndexStats()
    : IndexSearchableStats()
{
}

MemoryIndexStats::MemoryIndexStats(const IMemoryIndex &index)
    : IndexSearchableStats(index)
{
}

MemoryIndexStats::~MemoryIndexStats()
{
}

} // namespace searchcorespi::index
