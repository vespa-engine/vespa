// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_searchable_stats.h"
#include "indexsearchable.h"


namespace searchcorespi::index {

IndexSearchableStats::IndexSearchableStats()
    : _serialNum(0),
      _searchableStats()
{
}

IndexSearchableStats::IndexSearchableStats(const IndexSearchable &index)
    : _serialNum(index.getSerialNum()),
      _searchableStats(index.getSearchableStats())
{
}

bool IndexSearchableStats::operator<(const IndexSearchableStats &rhs) const
{
    return _serialNum < rhs._serialNum;
}

} // namespace searchcorespi::index
