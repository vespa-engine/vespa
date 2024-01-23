// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query.h"

namespace search::streaming {

/**
   N-ary Same element operator. All terms must be within the same element.
*/
class SameElementQueryNode : public AndQueryNode
{
public:
    SameElementQueryNode() noexcept : AndQueryNode("SAME_ELEMENT") { }
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_NOT; }
    void addChild(QueryNode::UP child) override;
};

}
