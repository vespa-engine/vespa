// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "groupengine.h"
#include <vespa/searchlib/aggregation/grouping.h>

namespace search::grouping {

class GroupingEngine
{
public:
    typedef std::vector<GroupEngine *> GroupEngines;
public:
    GroupingEngine(const GroupingEngine &) = delete;
    GroupingEngine & operator = (const GroupingEngine &) = delete;
    GroupingEngine(aggregation::Grouping & request);
    GroupingEngine(vespalib::nbostream & request, bool oldWay);
    ~GroupingEngine();
    vespalib::nbostream & serializeOldWay(vespalib::nbostream & request) const;
    vespalib::nbostream & serialize(vespalib::nbostream & request) const;
    void aggregate(const RankedHit * rankedHit, unsigned int len);
    void merge(const GroupingEngine & b);
    aggregation::Group::UP createResult() const;
    const GroupEngines & getEngines() const { return _levels; }
private:
    void fillRootRequest(const aggregation::Group & r);
    void preFillEngines(const aggregation::Group & r, size_t levels);
    aggregation::Grouping &        _request;
    GroupEngines                   _levels;
    aggregation::GroupingLevel     _rootRequestLevel;
};

}
