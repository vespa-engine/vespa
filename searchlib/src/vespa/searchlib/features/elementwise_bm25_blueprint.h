// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Blueprint for the elementwise bm25 ranking feature. It is created by the setup member function in the elementwise
 * ranking feature blueprint.
 *
 * This blueprint expects 3 parameters: index field name, dimension name and cell type.
 *
 * Example usage: elementwise(bm25(i),x,float) cause the elementwise ranking feature blueprint to create this blueprint
 * with parameters (i,x,float) and proxy calls to prepareSharedState() and createExecutor() to this blueprint.
 * The executor returned by createExecutor() will calculate bm25 feature per element in the field 'i', creating
 * a tensor with a single mapped dimension 'x' that contains an elementwise aggregated float bm25 score for each term
 * matching the field.
 */
class ElementwiseBm25Blueprint : public fef::Blueprint {
    const fef::FieldInfo* _field;
    double _k1_param;
    double _b_param;
    std::optional<double> _avg_element_length;
    vespalib::eval::ValueType              _output_tensor_type;
    std::unique_ptr<vespalib::eval::Value> _empty_output;
public:
    ElementwiseBm25Blueprint();
    ~ElementwiseBm25Blueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    std::unique_ptr<fef::Blueprint> createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;

};

}
