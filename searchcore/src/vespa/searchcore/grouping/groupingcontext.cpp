// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupingcontext.h"
#include <vespa/searchlib/aggregation/predicates.h>
#include <vespa/searchlib/aggregation/modifiers.h>

namespace search {

using aggregation::CountFS4Hits;
using aggregation::FS4HitSetDistributionKey;

namespace grouping {

void
GroupingContext::deserialize(const char *groupSpec, uint32_t groupSpecLen)
{
    if ((groupSpec != NULL) && (groupSpecLen > 4)) {
        vespalib::nbostream is(groupSpec, groupSpecLen);
        vespalib::NBOSerializer nis(is);
        uint32_t numGroupings = 0;
        nis >> numGroupings;
        for (size_t i = 0; i < numGroupings; i++) {
            GroupingPtr grouping(new search::aggregation::Grouping);
            grouping->deserialize(nis);
            grouping->setClock(&_clock);
            grouping->setTimeOfDoom(_timeOfDoom);
            _groupingList.push_back(grouping);
        }
    }
}

size_t
GroupingContext::countFS4Hits()
{
    size_t numFs4Hits(0);
    for (GroupingPtr & g : _groupingList) {
        CountFS4Hits counter;
        g->select(counter, counter);
        numFs4Hits += counter.getHitCount();
    }
    return numFs4Hits;
}

void
GroupingContext::setDistributionKey(uint32_t distributionKey)
{
    for (GroupingPtr & g : _groupingList) {
        FS4HitSetDistributionKey updater(distributionKey);
        g->select(updater, updater);
    }
}

GroupingContext::GroupingContext(const vespalib::Clock & clock, fastos::SteadyTimeStamp timeOfDoom, const char *groupSpec, uint32_t groupSpecLen) :
    _clock(clock),
    _timeOfDoom(timeOfDoom),
    _os(),
    _groupingList()
{
    deserialize(groupSpec, groupSpecLen);
}

GroupingContext::GroupingContext(const vespalib::Clock & clock, fastos::SteadyTimeStamp timeOfDoom) :
    _clock(clock),
    _timeOfDoom(timeOfDoom),
    _os(),
    _groupingList()
{
}

GroupingContext::GroupingContext(const GroupingContext & rhs) :
    _clock(rhs._clock),
    _timeOfDoom(rhs._timeOfDoom),
    _os(),
    _groupingList()
{
}

void
GroupingContext::addGrouping(const GroupingPtr & g)
{
    _groupingList.push_back(g);
}

void
GroupingContext::serialize()
{
    vespalib::NBOSerializer nos(_os);

    nos << (uint32_t)_groupingList.size();
    for (size_t i = 0; i < _groupingList.size(); i++) {
        search::aggregation::Grouping & grouping(*_groupingList[i]);
        grouping.serialize(nos);
    }
}

bool
GroupingContext::needRanking() const
{
    if (_groupingList.empty()) {
        return false;
    }
    return true;
}


} // namespace search::grouping
} // namespace search
