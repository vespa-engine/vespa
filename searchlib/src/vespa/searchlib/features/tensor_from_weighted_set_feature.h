// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_factory_blueprint.h"

namespace search {
namespace features {

/**
 * Feature blueprint for a rank feature that creates a tensor from a weighted set.
 * The weighted set source can be either an attribute vector or query parameter.
 */
class TensorFromWeightedSetBlueprint : public TensorFactoryBlueprint
{
public:
    TensorFromWeightedSetBlueprint();
    virtual search::fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new TensorFromWeightedSetBlueprint());
    }
    virtual search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().
            desc().string().
            desc().string().string();
    }
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params) override;
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search
