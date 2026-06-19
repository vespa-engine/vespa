// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_factory_blueprint.h"

#include <string>

namespace search::features {

/**
 * Blueprint for a rank feature that creates a tensor from an array
 * where the elements are used as labels in one dimension and the
 * array indices ("0", "1", ...) are used as labels in an offset dimension.
 * The tensor cells all get the value 1.0.
 *
 * The array source is an attribute vector.
 */
class TensorFromLabelsWithOffsetBlueprint : public TensorFactoryBlueprint {
private:
    std::string _offset_dimension;

public:
    TensorFromLabelsWithOffsetBlueprint();
    ~TensorFromLabelsWithOffsetBlueprint() override;
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new TensorFromLabelsWithOffsetBlueprint());
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string().string().string();
    }
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

} // namespace search::features
