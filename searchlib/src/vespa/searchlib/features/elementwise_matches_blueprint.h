// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Blueprint for the elementwise matches ranking feature. It is created by the setup member function in the
 * elementwise ranking feature blueprint.
 *
 * This blueprint expects 3 parameters: field name, dimension name and cell type. The field must be an array of struct
 * or map field (represented as a virtual field in the backend).
 *
 * Example usage: elementwise(matches(f),x,float) cause the elementwise ranking feature blueprint to create this
 * blueprint with parameters (f,x,float) and proxy calls to createExecutor() to this blueprint. The executor returned
 * by createExecutor() will create a tensor with a single mapped dimension 'x' that contains the value 1.0 for each
 * element id in the field 'f' that was matched by a sameElement query item searching the field.
 *
 * Element ids are taken from the positions in the match data for the sameElement query items, which have one
 * position per matching element.
 */
class ElementwiseMatchesBlueprint : public fef::Blueprint {
    const fef::FieldInfo*                  _field;
    vespalib::eval::ValueType              _output_tensor_type;
    std::unique_ptr<vespalib::eval::Value> _empty_output;

public:
    ElementwiseMatchesBlueprint();
    ~ElementwiseMatchesBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    std::unique_ptr<fef::Blueprint> createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

} // namespace search::features
