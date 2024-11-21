// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/util/index_stats.h>

namespace searchcorespi { class IndexSearchable; }

namespace searchcorespi::index {

/**
 * Information about a searchable index usable by state explorer.
 */
class IndexSearchableStats
{
    using SerialNum = search::SerialNum;
    using IndexStats = search::IndexStats;
    SerialNum       _serialNum;
    IndexStats _index_stats;
public:
    IndexSearchableStats();
    IndexSearchableStats(const IndexSearchable &index);
    bool operator<(const IndexSearchableStats &rhs) const;
    SerialNum getSerialNum() const { return _serialNum; }
    const IndexStats &get_index_stats() const { return _index_stats; }
};

}
