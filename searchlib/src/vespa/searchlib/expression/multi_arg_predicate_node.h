// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressiontree.h"
#include "filter_predicate_node.h"

namespace search::expression {

/**
 * Abstract base class for predicate nodes with multiple children.
 **/
class MultiArgPredicateNode : public FilterPredicateNode {
    using FilterPredicateNodeVector = std::vector<FilterPredicateNode::IP>;
    FilterPredicateNodeVector _args;

public:
    MultiArgPredicateNode() noexcept;
    ~MultiArgPredicateNode() override;
    MultiArgPredicateNode(const MultiArgPredicateNode&);
    MultiArgPredicateNode& operator=(const MultiArgPredicateNode&);

    explicit MultiArgPredicateNode(const std::vector<FilterPredicateNode::IP>& input);

    DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, expression, MultiArgPredicateNode);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate,
                       vespalib::ObjectOperation& operation) override;

    [[nodiscard]] FilterPredicateNodeVector& args() noexcept { return _args; }
    [[nodiscard]] const FilterPredicateNodeVector& args() const noexcept { return _args; }
};
}
