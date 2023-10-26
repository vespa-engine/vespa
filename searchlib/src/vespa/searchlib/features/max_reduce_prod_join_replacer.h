// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "internal_max_reduce_prod_join_feature.h"
#include <vespa/searchlib/features/rankingexpression/expression_replacer.h>

namespace search::features {

/**
 * ExpressionReplacer that will replacing expressions on the form:
 *
 *      reduce(
 *          join(
 *              tensorFromLabels(attribute(A), dim),
 *              tensorFromWeightedset(query(Q), dim),
 *              f(x,y)(x*y)
 *          ),
 *          max
 *      )
 *
 * With a parameterized (A, Q) adaption of the given blueprint
 * (default: InternalMaxReduceProdJoinBlueprint).
 **/
struct MaxReduceProdJoinReplacer {
    using ExpressionReplacer = rankingexpression::ExpressionReplacer;
    static ExpressionReplacer::UP create(fef::Blueprint::UP proto);
    static ExpressionReplacer::UP create() {
        return create(std::make_unique<InternalMaxReduceProdJoinBlueprint>());
    }
};

} // namespace search::features
