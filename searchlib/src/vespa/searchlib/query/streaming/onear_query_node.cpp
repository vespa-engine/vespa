// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onear_query_node.h"

namespace search::streaming {

bool
ONearQueryNode::evaluate() const
{
  bool ok(NearQueryNode::evaluate());
  return ok;
}

}
