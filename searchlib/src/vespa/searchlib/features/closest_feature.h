// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <optional>

namespace search::features {

/**
 * Implements the blueprint for the closest executor.
 */
class ClosestBlueprint : public fef::Blueprint {
    vespalib::string                       _field_name;
    vespalib::eval::ValueType              _field_tensor_type;
    vespalib::eval::ValueType              _output_tensor_type;
    uint32_t                               _field_id;
    std::optional<vespalib::string>        _item_label;
    std::unique_ptr<vespalib::eval::Value> _empty_output;
    std::vector<char>                      _identity_space;
    vespalib::eval::TypedCells             _identity_cells;
public:
    ClosestBlueprint();
    ~ClosestBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    std::unique_ptr<fef::Blueprint> createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
