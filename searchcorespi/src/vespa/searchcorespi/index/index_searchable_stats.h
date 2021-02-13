// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/util/searchable_stats.h>

namespace searchcorespi { class IndexSearchable; }

namespace searchcorespi::index {

/**
 * Information about a searchable index usable by state explorer.
 */
class IndexSearchableStats
{
    using SerialNum = search::SerialNum;
    using SearchableStats = search::SearchableStats;
    SerialNum       _serialNum;
    SearchableStats _searchableStats;
public:
    IndexSearchableStats();
    IndexSearchableStats(const IndexSearchable &index);
    bool operator<(const IndexSearchableStats &rhs) const;
    SerialNum getSerialNum() const { return _serialNum; }
    const SearchableStats &getSearchableStats() const { return _searchableStats; }
};

}
