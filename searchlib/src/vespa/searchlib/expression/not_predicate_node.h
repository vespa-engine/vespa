// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"

namespace search::expression {

/**
 * @class NotPredicateNode
 * @brief Implements logical NOT filter used in grouping expressions for negating sub filter expressions.
 **/
class NotPredicateNode : public FilterPredicateNode {
    FilterPredicateNode::CP _expression;

public:
    NotPredicateNode() noexcept;
    ~NotPredicateNode();
    NotPredicateNode* clone() const override { return new NotPredicateNode(*this); }

    // for unit testing::
    NotPredicateNode(const FilterPredicateNode& input);

    DECLARE_IDENTIFIABLE_NS2(search, expression, NotPredicateNode);
    DECLARE_NBO_SERIALIZE;

    bool allow(DocId docId, HitRank rank) override;
    bool allow(const document::Document &, HitRank) override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate,
                       vespalib::ObjectOperation& operation) override;
};

} // namespace search::expression
