// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hitsaggregationresult.h"
#include <vespa/document/fieldvalue/document.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.aggregation.hitsaggregationresult");

namespace search::aggregation {

using vespalib::FieldBase;
using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, HitsAggregationResult, AggregationResult);

HitsAggregationResult::HitsAggregationResult() :
    AggregationResult(),
    _summaryClass("default"),
    _maxHits(std::numeric_limits<uint32_t>::max()),
    _hits(),
    _isOrdered(false),
    _bestHitRank(),
    _summaryGenerator(0)
{}
HitsAggregationResult::~HitsAggregationResult() {}

void HitsAggregationResult::onPrepare(const ResultNode & result, bool useForInit)
{
    (void) result;
    (void) useForInit;
}

void
HitsAggregationResult::onMerge(const AggregationResult &b)
{
    const HitsAggregationResult &rhs = (const HitsAggregationResult &)b;
    _hits.onMerge(rhs._hits);
}

void
HitsAggregationResult::onAggregate(const ResultNode &result, DocId docId, HitRank rank)
{
    (void) result;
    if ( ! _isOrdered || (_hits.size() < _maxHits)) {
        _hits.addHit(FS4Hit(docId, rank), _maxHits);
    }
}

void
HitsAggregationResult::onAggregate(const ResultNode & result, const document::Document & doc, HitRank rank)
{
    (void) result;
    LOG(spam, "Filling vdshit for %s hits=%lu, maxHits=%u", doc.getId().toString().c_str(), (unsigned long)_hits.size(), _maxHits);
    if (!_isOrdered || (_hits.size() < _maxHits)) {
        VdsHit hit(doc.getId().toString(), rank);
        vespalib::ConstBufferRef docsum(_summaryGenerator->fillSummary(0, _summaryClass));
        hit.setSummary(docsum.c_str(), docsum.size());
        LOG(spam, "actually filled %s with summary %s with blob of size %lu", doc.getId().toString().c_str(),_summaryClass.c_str(), docsum.size() );
        _hits.addHit(hit, _maxHits);
    }
}

void
HitsAggregationResult::onAggregate(const ResultNode & result)
{
    (void) result;
    LOG_ABORT("should not reach here");
}

void
HitsAggregationResult::onReset()
{
    _hits.clear();
}

Serializer &
HitsAggregationResult::onSerialize(Serializer & os) const
{
    AggregationResult::onSerialize(os);
    os << _summaryClass << _maxHits;
    _hits.serialize(os);
    return os;
}

Deserializer &
HitsAggregationResult::onDeserialize(Deserializer & is)
{
    AggregationResult::onDeserialize(is);
    is >> _summaryClass >> _maxHits;
    _hits.deserialize(is);
    if (_maxHits == 0) {
        _maxHits = std::numeric_limits<uint32_t>::max();
    }
    return is;
}

void
HitsAggregationResult::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    AggregationResult::visitMembers(visitor);
    visit(visitor, "summaryClass", _summaryClass);
    visit(visitor, "maxHits", _maxHits);
    _hits.visitMembers(visitor);
}

void
HitsAggregationResult::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    AggregationResult::selectMembers(predicate, operation);
    _hits.selectMembers(predicate, operation);
}

const expression::ResultNode &
HitsAggregationResult::onGetRank() const
{
    if ( ! _hits.empty() ) {
        _bestHitRank = _hits.front().getRank();
    }
    return _bestHitRank;
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_hitsaggregationresult() {}
