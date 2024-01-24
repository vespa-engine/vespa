// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query.h"

namespace search::streaming {

/**
   N-ary Near operator. All terms must be within the given distance.
*/
class NearQueryNode : public AndQueryNode
{
protected:
    template <bool ordered>
    bool evaluate_helper() const;
public:
    NearQueryNode() noexcept : AndQueryNode("NEAR"), _distance(0) { }
    explicit NearQueryNode(const char * opName) noexcept : AndQueryNode(opName), _distance(0) { }
    bool evaluate() const override;
    void distance(size_t dist)       { _distance = dist; }
    size_t distance()          const { return _distance; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    bool isFlattenable(ParseItem::ItemType) const override { return false; }
private:
    size_t _distance;
};

}
