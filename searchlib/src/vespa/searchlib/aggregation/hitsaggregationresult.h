// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include "hitlist.h"
#include <vespa/searchlib/expression/floatresultnode.h>

namespace search::aggregation {

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
    void onPrepare(const ResultNode & result, bool useForInit) override;
    void onAggregate(const ResultNode &result, DocId docId, HitRank rank) override;
    void onAggregate(const ResultNode &result, const document::Document & doc, HitRank rank) override;
    const ResultNode & onGetRank() const override;

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
        void execute(vespalib::Identifiable &obj) override { static_cast<HitsAggregationResult &>(obj)._isOrdered = true; }
        bool check(const vespalib::Identifiable &obj) const override { return obj.getClass().inherits(HitsAggregationResult::classId); }
    };

    DECLARE_AGGREGATIONRESULT(HitsAggregationResult);
    HitsAggregationResult();
    HitsAggregationResult(HitsAggregationResult &&) noexcept;
    HitsAggregationResult & operator=(HitsAggregationResult &&) noexcept;
    HitsAggregationResult(const HitsAggregationResult &);
    HitsAggregationResult & operator=(const HitsAggregationResult &);
    ~HitsAggregationResult() override;
    void postMerge() override { _hits.postMerge(_maxHits); }
    void setSummaryGenerator(SummaryGenerator & summaryGenerator) { _summaryGenerator = &summaryGenerator; }
    const SummaryClassType & getSummaryClass() const { return _summaryClass; }
    HitsAggregationResult setSummaryClass(const SummaryClassType & summaryClass) { _summaryClass = summaryClass; return *this; }
    HitsAggregationResult &setMaxHits(uint32_t maxHits) {
        _maxHits = (maxHits == 0) ? std::numeric_limits<uint32_t>::max() : maxHits;
        return *this;
    }
    HitsAggregationResult & addHit(const FS4Hit &hit) { _hits.addHit(hit, _maxHits); return *this; }
    HitsAggregationResult & addHit(const VdsHit &hit) { _hits.addHit(hit, _maxHits); return *this; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate &predicate, vespalib::ObjectOperation &operation) override;
    HitsAggregationResult & sort() { _hits.sort(); return *this; }
    const ResultNode * getResult() const override { return &_hits; }
    ResultNode & getResult() override { return _hits; }
};

}
