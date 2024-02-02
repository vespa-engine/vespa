// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onear_query_node.h"
#include "hit_iterator_pack.h"

namespace search::streaming {

bool
ONearQueryNode::evaluate() const
{
    return evaluate_helper<true>();
}

}
