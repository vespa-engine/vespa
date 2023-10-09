// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "intrinsic_expression.h"
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search::fef { class Blueprint; }
namespace search::fef { class IIndexEnvironment; }

namespace search::features::rankingexpression {

/**
 * Adapt a Blueprint with no inputs and a single output to the
 * IntrinsicExpression interface.
 **/
struct IntrinsicBlueprintAdapter
{
    static IntrinsicExpression::UP try_create(const search::fef::Blueprint &proto,
                                              const search::fef::IIndexEnvironment &env,
                                              const std::vector<vespalib::string> &params);
};

} // namespace search::features::rankingexpression
