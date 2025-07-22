// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"

namespace search::expression {

/**
 * @class MultiArgPredicateNode
 * @brief Abstract base class for predicate nodes with multiple children.
 **/
class MultiArgPredicateNode : public FilterPredicateNode {
    using FilterPredicateNodeVector = std::vector<FilterPredicateNode::CP>;
    FilterPredicateNodeVector _args;

public:
    DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, expression, MultiArgPredicateNode);

    MultiArgPredicateNode() noexcept;
    ~MultiArgPredicateNode() override;

    MultiArgPredicateNode(const std::vector<FilterPredicateNode>& input);


    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate,
                       vespalib::ObjectOperation& operation) override;

    FilterPredicateNodeVector& args() { return _args; }
    const FilterPredicateNodeVector& args() const { return _args; }
};
}
