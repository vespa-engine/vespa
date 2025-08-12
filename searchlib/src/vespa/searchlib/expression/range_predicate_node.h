// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"

namespace search::expression {

/**
 **/
class RangePredicateNode : public FilterPredicateNode {
    double _lower;
    double _upper;
    bool _lower_inclusive;
    bool _upper_inclusive;
    ExpressionTree _argument;

    [[nodiscard]] bool satisfies_bounds(double value) const;
    [[nodiscard]] bool check(const ResultNode* result) const;

public:
    RangePredicateNode() noexcept;
    ~RangePredicateNode() override;
    RangePredicateNode(const RangePredicateNode&);
    RangePredicateNode& operator=(const RangePredicateNode&);

    RangePredicateNode* clone() const override { return new RangePredicateNode(*this); }

    // for unit testing::
    RangePredicateNode(double lower, double upper, bool lower_inclusive, bool upper_inclusive, ExpressionNode::UP input);

    DECLARE_IDENTIFIABLE_NS2(search, expression, RangePredicateNode);
    DECLARE_NBO_SERIALIZE;
    bool allow(DocId docId, HitRank rank) override;
    bool allow(const document::Document&, HitRank) override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate,
                       vespalib::ObjectOperation& operation) override;
};

} // namespace search::expression
