// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"
#include "multi_arg_predicate_node.h"

namespace search::expression {

/**
 **/
class OrPredicateNode : public MultiArgPredicateNode {
public:
    DECLARE_NBO_SERIALIZE;

    DECLARE_IDENTIFIABLE_NS2(search, expression, OrPredicateNode);

    OrPredicateNode() noexcept;
    ~OrPredicateNode() override;
    OrPredicateNode* clone() const override { return new OrPredicateNode(*this); }

    // for unit testing::
    OrPredicateNode(const std::vector<FilterPredicateNode>& input);

    bool allow(DocId docId, HitRank rank) override;
    bool allow(const document::Document &, HitRank) override;
};

} // namespace search::expression
