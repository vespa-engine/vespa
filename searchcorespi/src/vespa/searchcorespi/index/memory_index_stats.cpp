// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "memory_index_stats.h"
#include "imemoryindex.h"


namespace searchcorespi {
namespace index {

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
} // namespace searchcorespi
