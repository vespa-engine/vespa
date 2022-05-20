// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_index_stats.h"
#include "idiskindex.h"


namespace searchcorespi::index {

DiskIndexStats::DiskIndexStats()
    : IndexSearchableStats(),
      _indexDir()
{
}

DiskIndexStats::DiskIndexStats(const IDiskIndex &index)
    : IndexSearchableStats(index),
      _indexDir(index.getIndexDir())
{
}

DiskIndexStats::~DiskIndexStats()
{
}

}
