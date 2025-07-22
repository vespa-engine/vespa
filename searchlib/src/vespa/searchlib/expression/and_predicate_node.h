// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"
#include "multi_arg_predicate_node.h"

namespace search::expression {

/**
 * Implements logical AND filter used in grouping expressions.
 **/
class AndPredicateNode : public MultiArgPredicateNode {
public:
    AndPredicateNode() noexcept;
    ~AndPredicateNode() override;
    AndPredicateNode* clone() const override { return new AndPredicateNode(*this); }

    // for unit testing::
    AndPredicateNode(const std::vector<FilterPredicateNode>& input);

    DECLARE_NBO_SERIALIZE;
    DECLARE_IDENTIFIABLE_NS2(search, expression, AndPredicateNode);

    bool allow(DocId docId, HitRank rank) override;
    bool allow(const document::Document &, HitRank) override;
};

} // namespace search::expression
