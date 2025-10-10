// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querynode.h"

namespace search::streaming {

const HitList & QueryNode::evaluateHits(HitList & hl)
{
    return hl;
}

}
