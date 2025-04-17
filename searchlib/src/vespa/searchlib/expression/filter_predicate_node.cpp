// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "filter_predicate_node.h"

namespace search::expression {

IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, expression, FilterPredicateNode, vespalib::Identifiable);

// IMPLEMENT_IDENTIFIABLE_NS2(search, expression, TruePredicateNode, FilterPredicateNode);

TruePredicateNode TruePredicateNode::instance;

} // namespace search::expression
