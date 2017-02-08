// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include "hitlist.h"
#include <vespa/searchlib/expression/floatresultnode.h>

namespace search {
namespace aggregation {

class HitsAggregationResult : public AggregationResult
{
public:
    using FloatResultNode = expression::FloatResultNode;
    using SummaryClassType = vespalib::string;

    class SummaryGenerator
    {
    public:
        virtual ~SummaryGenerator() { }
        virtual vespalib::ConstBufferRef fillSummary(DocId lid, const SummaryClassType & summaryClass) = 0;
    };

private:
    virtual void onPrepare(const ResultNode & result, bool useForInit);

    virtual void onAggregate(const ResultNode &result, DocId docId, HitRank rank);
    virtual void onAggregate(const ResultNode &result, const document::Document & doc, HitRank rank);
    virtual const ResultNode & onGetRank() const;

    SummaryClassType          _summaryClass;
    uint32_t                  _maxHits;
    HitList                   _hits;
    bool                      _isOrdered;
    mutable FloatResultNode   _bestHitRank;
    SummaryGenerator         *_summaryGenerator;

public:
    class SetOrdered : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    private:
        virtual void execute(vespalib::Identifiable &obj) { static_cast<HitsAggregationResult &>(obj)._isOrdered = true; }
        virtual bool check(const vespalib::Identifiable &obj) const { return obj.getClass().inherits(HitsAggregationResult::classId); }
    };

    DECLARE_AGGREGATIONRESULT(HitsAggregationResult);
    HitsAggregationResult() :
        AggregationResult(),
        _summaryClass("default"),
        _maxHits(std::numeric_limits<uint32_t>::max()),
        _hits(),
        _isOrdered(false),
        _bestHitRank(),
        _summaryGenerator(0)
    {}
    virtual void postMerge() { _hits.postMerge(_maxHits); }
    void setSummaryGenerator(SummaryGenerator & summaryGenerator) { _summaryGenerator = &summaryGenerator; }
    const SummaryClassType & getSummaryClass() const { return _summaryClass; }
    HitsAggregationResult setSummaryClass(const SummaryClassType & summaryClass) { _summaryClass = summaryClass; return *this; }
    HitsAggregationResult &setMaxHits(uint32_t maxHits) {
        _maxHits = (maxHits == 0) ? std::numeric_limits<uint32_t>::max() : maxHits;
        return *this;
    }
    HitsAggregationResult & addHit(const FS4Hit &hit) { _hits.addHit(hit, _maxHits); return *this; }
    HitsAggregationResult & addHit(const VdsHit &hit) { _hits.addHit(hit, _maxHits); return *this; }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    virtual void selectMembers(const vespalib::ObjectPredicate &predicate,
                               vespalib::ObjectOperation &operation);
    HitsAggregationResult & sort() { _hits.sort(); return *this; }
    virtual const ResultNode & getResult() const { return _hits; }
    virtual ResultNode & getResult() { return _hits; }
};

}
}

