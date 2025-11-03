// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_factory_blueprint.h"
#include <vespa/eval/eval/value_type.h>

namespace search::features {

/**
 * Blueprint for a rank feature that creates a tensor from struct field attributes.
 * Takes two struct fields: one for dimension labels (keys) and one for cell values.
 *
 * Signature: tensorFromStructs(attribute(baseAttr), keyField, valueField, type)
 * Example: tensorFromStructs(attribute(items), "itemname", "price", "float")
 *   - Creates tensor<float>(itemname{})
 *   - Labels from items.itemname attribute
 *   - Values from items.price attribute
 */
class TensorFromStructsBlueprint : public TensorFactoryBlueprint
{
private:
    std::string _keyField;
    std::string _valueField;
    vespalib::eval::CellType _cellType;

public:
    TensorFromStructsBlueprint();
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new TensorFromStructsBlueprint());
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().
            desc().string().string().string().string();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
