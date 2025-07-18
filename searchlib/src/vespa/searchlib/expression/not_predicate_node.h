// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"

namespace search::expression {

/**
 **/
class NotPredicateNode : public FilterPredicateNode {
private:
    ExpressionTree _argument;

    bool check(const ResultNode* result) const;
public:
    NotPredicateNode() noexcept;
    ~NotPredicateNode();
    NotPredicateNode* clone() const override { return new NotPredicateNode(*this); }

    // for unit testing::
    RegexPredicateNode(std::string regex, ExpressionNode::UP input);

    DECLARE_IDENTIFIABLE_NS2(search, expression, RegexPredicateNode);
    DECLARE_NBO_SERIALIZE;
    bool allow(DocId docId, HitRank rank) override;
    bool allow(const document::Document &, HitRank) override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate,
                       vespalib::ObjectOperation& operation) override;
};

} // namespace search::expression
