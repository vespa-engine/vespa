// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "aggregation.h"

#include "expressioncountaggregationresult.h"

#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/searchlib/aggregation/aggregation.hpp>
#include <vespa/vespalib/objects/visit.hpp>

#include <xxhash.h>

using namespace search::expression;

namespace search::aggregation {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_ABSTRACT_AGGREGATIONRESULT(AggregationResult, ExpressionNode);
IMPLEMENT_AGGREGATIONRESULT(ExpressionCountAggregationResult, AggregationResult);

AggregationResult::AggregationResult() : _expressionTree(std::make_shared<ExpressionTree>()), _tag(-1) {
}

AggregationResult::AggregationResult(const AggregationResult&) = default;
AggregationResult& AggregationResult::operator=(const AggregationResult&) = default;

AggregationResult::~AggregationResult() = default;

void AggregationResult::aggregate(const document::Document& doc, HitRank rank) {
    _expressionTree->execute(doc, rank);
    onAggregate(*_expressionTree->getResult(), doc, rank);
}

void AggregationResult::aggregate(DocId docId, HitRank rank) {
    _expressionTree->execute(docId, rank);
    onAggregate(*_expressionTree->getResult(), docId, rank);
}

bool AggregationResult::Configure::check(const vespalib::Identifiable& obj) const {
    return obj.inherits(AggregationResult::classId);
}

void AggregationResult::Configure::execute(vespalib::Identifiable& obj) {
    auto& a(static_cast<AggregationResult&>(obj));
    a.prepare();
}

AggregationResult& AggregationResult::setExpression(ExpressionNode::UP expr) {
    _expressionTree = std::make_shared<ExpressionTree>(std::move(expr));
    prepare(_expressionTree->getResult());
    return *this;
}

Serializer& AggregationResult::onSerialize(Serializer& os) const {
    return (os << *_expressionTree).put(_tag);
}

Deserializer& AggregationResult::onDeserialize(Deserializer& is) {
    _expressionTree = std::make_shared<ExpressionTree>();
    return (is >> *_expressionTree).get(_tag);
}

void AggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "expression", _expressionTree);
}

void AggregationResult::selectMembers(const vespalib::ObjectPredicate& predicate,
                                      vespalib::ObjectOperation&       operation) {
    _expressionTree->select(predicate, operation);
}

namespace {
// Calculates the sum of all buckets.
template <int BucketBits, typename HashT> int calculateRank(const Sketch<BucketBits, HashT>& sketch) {
    if (sketch.getClassId() == SparseSketch<BucketBits, HashT>::classId) {
        return static_cast<const SparseSketch<BucketBits, HashT>&>(sketch).getSize();
    }
    auto normal = static_cast<const NormalSketch<BucketBits, HashT>&>(sketch);
    int  rank = 0;
    for (size_t i = 0; i < sketch.BUCKET_COUNT; ++i) {
        rank += normal.bucket[i];
    }
    return rank;
}
} // namespace

void ExpressionCountAggregationResult::onMerge(const AggregationResult& r) {
    const auto& result = Identifiable::cast<const ExpressionCountAggregationResult&>(r);
    _hll.merge(result._hll);
    _rank.set(calculateRank(_hll.getSketch()));
}
void ExpressionCountAggregationResult::onAggregate(const ResultNode& result) {
    size_t             hash = result.hash();
    const unsigned int seed = 42;
    hash = XXH32(&hash, sizeof(hash), seed);
    // The rank is a maintained sum of all buckets. This should give
    // almost the same ordering as the actual estimates.
    _rank += _hll.aggregate(hash);
}
void ExpressionCountAggregationResult::onReset() {
    _hll = HyperLogLog<PRECISION>();
    _rank.set(0);
}
Serializer& ExpressionCountAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    _hll.serialize(os);
    return os;
}
Deserializer& ExpressionCountAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    _hll.deserialize(is);
    _rank.set(calculateRank(_hll.getSketch()));
    return is;
}

ExpressionCountAggregationResult::ExpressionCountAggregationResult() = default;
ExpressionCountAggregationResult::~ExpressionCountAggregationResult() = default;

} // namespace search::aggregation

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_aggregation() {
}
