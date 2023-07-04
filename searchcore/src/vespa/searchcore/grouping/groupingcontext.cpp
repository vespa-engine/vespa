// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupingcontext.h"
#include <vespa/searchlib/aggregation/predicates.h>
#include <vespa/searchlib/aggregation/modifiers.h>
#include <vespa/searchlib/aggregation/hitsaggregationresult.h>
#include <vespa/searchlib/common/bitvector.h>

namespace search::grouping {

using aggregation::CountFS4Hits;
using aggregation::FS4HitSetDistributionKey;

void
GroupingContext::deserialize(const char *groupSpec, uint32_t groupSpecLen)
{
    if ((groupSpec != nullptr) && (groupSpecLen > 4)) {
        vespalib::nbostream is(groupSpec, groupSpecLen);
        vespalib::NBOSerializer nis(is);
        uint32_t numGroupings = 0;
        nis >> numGroupings;
        _groupingList.reserve(numGroupings);
        for (size_t i = 0; i < numGroupings; i++) {
            auto grouping = std::make_shared<search::aggregation::Grouping>();
            grouping->deserialize(nis);
            _groupingList.push_back(std::move(grouping));
        }
    }
}

size_t
GroupingContext::countFS4Hits()
{
    size_t numFs4Hits(0);
    for (auto & g : _groupingList) {
        CountFS4Hits counter;
        g->select(counter, counter);
        numFs4Hits += counter.getHitCount();
    }
    return numFs4Hits;
}

void
GroupingContext::setDistributionKey(uint32_t distributionKey)
{
    for (auto & g : _groupingList) {
        FS4HitSetDistributionKey updater(distributionKey);
        g->select(updater, updater);
    }
}

GroupingContext::GroupingContext(const vespalib::Clock & clock, vespalib::steady_time timeOfDoom,
                                 const char *groupSpec, uint32_t groupSpecLen, bool enableNested)
    : _clock(clock),
      _timeOfDoom(timeOfDoom),
      _os(),
      _groupingList(),
      _enableNestedMultivalueGrouping(enableNested)
{
    deserialize(groupSpec, groupSpecLen);
}

GroupingContext::GroupingContext(const vespalib::Clock & clock, vespalib::steady_time timeOfDoom)
    : _clock(clock),
      _timeOfDoom(timeOfDoom),
      _os(),
      _groupingList(),
      _enableNestedMultivalueGrouping(true)
{ }

GroupingContext::GroupingContext(const GroupingContext & rhs) :
    _clock(rhs._clock),
    _timeOfDoom(rhs._timeOfDoom),
    _os(),
    _groupingList(),
    _enableNestedMultivalueGrouping(rhs._enableNestedMultivalueGrouping)
{ }

void
GroupingContext::addGrouping(std::shared_ptr<Grouping> g)
{
    _groupingList.push_back(std::move(g));
}

void
GroupingContext::serialize()
{
    vespalib::NBOSerializer nos(_os);

    nos << (uint32_t)_groupingList.size();
    for (const auto & grouping : _groupingList) {
        grouping->serialize(nos);
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

using DocId = uint32_t;

unsigned int
GroupingContext::aggregateRanked(Grouping &grouping, const RankedHit *rankedHit, unsigned int len) const {
    unsigned int i(0);
    for(; (i < len) && !hasExpired(); i++) {
        grouping.aggregate(rankedHit[i].getDocId(), rankedHit[i].getRank());
    }
    return i;
}

void
GroupingContext::aggregate(Grouping & grouping, const BitVector * bVec, unsigned int lidLimit) const {
    for (DocId d(bVec->getFirstTrueBit()); (d < lidLimit) && !hasExpired(); d = bVec->getNextTrueBit(d+1)) {
        grouping.aggregate(d, 0.0);
    }
}
void
GroupingContext::aggregate(Grouping & grouping, const BitVector * bVec, unsigned int lidLimit, unsigned int topN) const {
    for(DocId d(bVec->getFirstTrueBit()), i(0); (d < lidLimit) && (i < topN) && !hasExpired(); d = bVec->getNextTrueBit(d+1), i++) {
        grouping.aggregate(d, 0.0);
    }
}

void
GroupingContext::aggregate(Grouping & grouping, const RankedHit * rankedHit, unsigned int len, const BitVector * bVec) const
{
    grouping.preAggregate(false);
    uint32_t count = aggregateRanked(grouping, rankedHit, grouping.getMaxN(len));
    if (bVec != nullptr) {
        int64_t topN = grouping.getTopN();
        if (topN > count) {
            aggregate(grouping, bVec, bVec->size(), topN - count);
        } else {
            aggregate(grouping, bVec, bVec->size());
        }
    }
    grouping.postProcess();
}

void
GroupingContext::aggregate(Grouping & grouping, const RankedHit * rankedHit, unsigned int len) const
{
    bool isOrdered(! grouping.needResort());
    grouping.preAggregate(isOrdered);
    search::aggregation::HitsAggregationResult::SetOrdered pred;
    grouping.select(pred, pred);
    aggregateRanked(grouping, rankedHit, grouping.getMaxN(len));
    grouping.postProcess();
}

}
