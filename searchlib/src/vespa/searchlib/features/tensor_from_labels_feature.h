// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_factory_blueprint.h"

namespace search {
namespace features {

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
    virtual search::fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new TensorFromLabelsBlueprint());
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
