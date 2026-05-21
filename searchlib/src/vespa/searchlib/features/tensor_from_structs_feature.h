// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_factory_blueprint.h"

#include <vespa/eval/eval/value_type.h>

namespace search::features {

/**
 * Blueprint for a rank feature that creates a tensor from struct field attributes.
 * Takes 1 to 5 key fields (each becomes a mapped dimension) plus one value field.
 *
 * Signatures:
 *   tensorFromStructs(attribute(baseAttr), keyField,                                    valueField, type)
 *   tensorFromStructs(attribute(baseAttr), keyField1, keyField2,                        valueField, type)
 *   tensorFromStructs(attribute(baseAttr), keyField1, keyField2, keyField3,             valueField, type)
 *   tensorFromStructs(attribute(baseAttr), keyField1, keyField2, keyField3, keyField4,  valueField, type)
 *   tensorFromStructs(attribute(baseAttr), keyField1, ...,                  keyField5,  valueField, type)
 *
 * Example: tensorFromStructs(attribute(items), "itemname", "price", "float")
 *   - Creates tensor<float>(itemname{})
 *   - Labels from items.itemname attribute
 *   - Values from items.price attribute
 */
class TensorFromStructsBlueprint : public TensorFactoryBlueprint {
private:
    std::vector<std::string> _keyFields;
    std::string              _valueField;
    vespalib::eval::CellType _cellType;

public:
    TensorFromStructsBlueprint();
    ~TensorFromStructsBlueprint() override;
    fef::Blueprint::UP createInstance() const override { return Blueprint::UP(new TensorFromStructsBlueprint()); }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string().string().string().string().string().repeat();
    }
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

} // namespace search::features
