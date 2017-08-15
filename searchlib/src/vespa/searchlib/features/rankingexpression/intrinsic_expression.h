// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib { class Stash; }

namespace search::fef {
class FeatureType;
class FeatureExecutor;
class IQueryEnvironment;
}

namespace search::features::rankingexpression {

/**
 * Interface representing an intrinsic expression replacing the
 * calculation of a ranking expression and its inputs.
 **/
struct IntrinsicExpression {
    using FeatureType = search::fef::FeatureType;
    using FeatureExecutor = search::fef::FeatureExecutor;
    using QueryEnv = search::fef::IQueryEnvironment;
    using UP = std::unique_ptr<IntrinsicExpression>;
    virtual FeatureType result_type() const = 0;
    virtual FeatureExecutor &create_executor(const QueryEnv &queryEnv,
                                             vespalib::Stash &stash) const = 0;
    virtual ~IntrinsicExpression();
};

} // namespace search::features::rankingexpression
