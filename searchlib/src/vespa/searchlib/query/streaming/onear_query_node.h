// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "near_query_node.h"

namespace search::streaming {

/**
   N-ary Ordered near operator. The terms must be in order and the distance between
   the first and last must not exceed the given distance.
*/
class ONearQueryNode : public NearQueryNode
{
public:
    explicit ONearQueryNode(const search::queryeval::IElementGapInspector& element_gap_inspector) noexcept
        : NearQueryNode("ONEAR", element_gap_inspector)
    { }
    bool evaluate() const override;
};

}
