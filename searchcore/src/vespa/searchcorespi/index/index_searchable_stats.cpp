// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_searchable_stats.h"
#include "indexsearchable.h"


namespace searchcorespi::index {

IndexSearchableStats::IndexSearchableStats()
    : _serialNum(0),
      _index_stats()
{
}

IndexSearchableStats::IndexSearchableStats(const IndexSearchable &index)
    : _serialNum(index.getSerialNum()),
      _index_stats(index.get_index_stats(false))
{
}

bool IndexSearchableStats::operator<(const IndexSearchableStats &rhs) const
{
    return _serialNum < rhs._serialNum;
}

} // namespace searchcorespi::index
