// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "disk_index_stats.h"
#include "memory_index_stats.h"
#include <vector>

namespace searchcorespi {

class IIndexManager;

/**
 * Information about an index manager usable by state explorer.
 */
class IndexManagerStats {
    std::vector<index::DiskIndexStats> _diskIndexes;
    std::vector<index::MemoryIndexStats> _memoryIndexes;
public:
    IndexManagerStats();
    IndexManagerStats(const IIndexManager &indexManager);
    ~IndexManagerStats();

    const std::vector<index::DiskIndexStats> &getDiskIndexes() const {
        return _diskIndexes;
    }
    const std::vector<index::MemoryIndexStats> &getMemoryIndexes() const {
        return _memoryIndexes;
    }
};

} // namespace searchcorespi
