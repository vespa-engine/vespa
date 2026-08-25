// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "aggregation.h"

#include <vespa/searchlib/aggregation/aggregation.hpp>
#include <vespa/vespalib/objects/visit.hpp>

using namespace search::expression;

namespace search::aggregation {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_ABSTRACT_AGGREGATIONRESULT(AggregationResult, ExpressionNode);

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

} // namespace search::aggregation

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_aggregation() {
}
