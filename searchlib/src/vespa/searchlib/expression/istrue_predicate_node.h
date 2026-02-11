// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"

namespace search::expression {

/**
 * Implements istrue filter in grouping expressions.
 *
 * Checks if a boolean expression evaluates to true. The expression must
 * evaluate to a BoolResultNode, otherwise grouping will fail with an error.
 **/
class IsTruePredicateNode : public FilterPredicateNode {
    ExpressionTree _expression;

    [[nodiscard]] bool check(const ResultNode* result) const;

public:
    IsTruePredicateNode() noexcept;
    ~IsTruePredicateNode() override;
    IsTruePredicateNode(const IsTruePredicateNode&);
    IsTruePredicateNode& operator=(const IsTruePredicateNode&);

    [[nodiscard]] IsTruePredicateNode* clone() const override { return new IsTruePredicateNode(*this); }

    // for unit testing:
    explicit IsTruePredicateNode(ExpressionNode::UP input);

    DECLARE_IDENTIFIABLE_NS2(search, expression, IsTruePredicateNode);
    DECLARE_NBO_SERIALIZE;
    bool allow(DocId docId, HitRank rank) override;
    bool allow(const document::Document&, HitRank) override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate,
                       vespalib::ObjectOperation& operation) override;
};

} // namespace search::expression
