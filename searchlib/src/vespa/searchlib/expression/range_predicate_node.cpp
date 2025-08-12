// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "range_predicate_node.h"
#include "resultnode.h"
#include "resultvector.h"

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, RangePredicateNode, FilterPredicateNode);

bool RangePredicateNode::satisfies_bounds(double value) const {
    bool satisfy_lower = false;
    if (_lower_inclusive) {
        satisfy_lower = value >= _lower;
    } else {
        satisfy_lower = value > _lower;
    }

    bool satisfy_upper = false;
    if (_upper_inclusive) {
        satisfy_upper = value <= _upper;
    } else {
        satisfy_upper = value < _upper;
    }

    return satisfy_lower && satisfy_upper;
}

bool RangePredicateNode::check(const ResultNode* result) const {
    if (result->inherits(ResultNodeVector::classId)) {
        const auto * rv = static_cast<const ResultNodeVector *>(result);
        for (size_t i = 0; i < rv->size(); i++) {
            if (satisfies_bounds(rv[i].getFloat())) {
                return true;
            }
        }
        return false;
    }

    return satisfies_bounds(result->getFloat());
}

bool RangePredicateNode::allow(const document::Document& doc, HitRank rank) {
    if (_argument.getRoot()) {
        _argument.execute(doc, rank);
        return check(_argument.getResult());
    }
    return false;
}

bool RangePredicateNode::allow(DocId docId, HitRank rank) {
    if (_argument.getRoot()) {
        _argument.execute(docId, rank);
        return check(_argument.getResult());
    }
    return false;
}

RangePredicateNode::RangePredicateNode() noexcept = default;

RangePredicateNode::~RangePredicateNode() = default;

RangePredicateNode::RangePredicateNode(const RangePredicateNode&) = default;

RangePredicateNode& RangePredicateNode::operator=(const RangePredicateNode&) = default;

RangePredicateNode::RangePredicateNode(double lower, double upper, bool lower_inclusive, bool upper_inclusive, ExpressionNode::UP input)
  : _lower(lower),
    _upper(upper),
    _lower_inclusive(lower_inclusive),
    _upper_inclusive(upper_inclusive),
    _argument(std::move(input))
{
}


Serializer& RangePredicateNode::onSerialize(Serializer& os) const {
    return os << _lower << _upper << _lower_inclusive << _upper_inclusive << _argument;
}

Deserializer& RangePredicateNode::onDeserialize(Deserializer& is) {
    is >> _lower;
    is >> _upper;
    is >> _lower_inclusive;
    is >> _upper_inclusive;
    is >> _argument;
    return is;
}

void RangePredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "lower", _lower);
    visit(visitor, "upper", _upper);
    visit(visitor, "lower_inclusive", _lower_inclusive);
    visit(visitor, "upper_inclusive", _upper_inclusive);
    visit(visitor, "argument", _argument);
}

void RangePredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                       vespalib::ObjectOperation& operation) {
    _argument.select(predicate, operation);
}

} // namespace search::expression
