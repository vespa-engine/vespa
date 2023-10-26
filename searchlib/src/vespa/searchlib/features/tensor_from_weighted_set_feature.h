// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_factory_blueprint.h"

namespace search::features {

/**
 * Feature blueprint for a rank feature that creates a tensor from a weighted set.
 * The weighted set source can be either an attribute vector or query parameter.
 */
class TensorFromWeightedSetBlueprint : public TensorFactoryBlueprint
{
public:
    TensorFromWeightedSetBlueprint();
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new TensorFromWeightedSetBlueprint());
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().
            desc().string().
            desc().string().string();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
