// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"

#include <vector>

namespace search::expression {

/**
 * Implements in filter in grouping expressions.
 *
 * Checks if the string value of an expression is equal to the string value of one of the arguments.
 **/
class InPredicateNode : public FilterPredicateNode {
    ExpressionTree              _argument;
    std::vector<ExpressionTree> _args;

    [[nodiscard]] bool check(const ResultNode* result) const;

public:
    InPredicateNode() noexcept;
    ~InPredicateNode() override;
    InPredicateNode(const InPredicateNode&);
    InPredicateNode& operator=(const InPredicateNode&);

    [[nodiscard]] InPredicateNode* clone() const override { return new InPredicateNode(*this); }

    // for unit testing::
    InPredicateNode(ExpressionNode::UP input, std::vector<ExpressionNode::UP> args);

    DECLARE_IDENTIFIABLE_NS2(search, expression, InPredicateNode);
    DECLARE_NBO_SERIALIZE;

    bool allow(DocId docId, HitRank rank) override;
    bool allow(const document::Document&, HitRank) override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;
};

} // namespace search::expression
