// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_factory_blueprint.h"

namespace search::features {

/**
 * Blueprint for a rank feature that creates a tensor from an array
 * where the elements in the array are used as labels in the tensor addresses.
 * The tensor cells all get the value 1.0.
 *
 * The array source can be either an attribute vector or query parameter.
 */
class TensorFromLabelsBlueprint : public TensorFactoryBlueprint
{
public:
    TensorFromLabelsBlueprint();
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new TensorFromLabelsBlueprint());
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
