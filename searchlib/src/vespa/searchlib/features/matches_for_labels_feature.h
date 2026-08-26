// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/blueprint.h>

#include <memory>

namespace search::features {

/**
 * Blueprint for matches_for_labels(field).
 *
 * Output is a mapped tensor<float>(label{}) of match bits for one field.
 * A query item label gets a cell (value 1) only if at least one term carrying
 * that label searched this field and matched the document. Other labels are
 * omitted.
 */
class MatchesForLabelsBlueprint : public fef::Blueprint {
private:
    uint32_t                               _field_id;
    vespalib::eval::ValueType              _value_type;
    std::unique_ptr<vespalib::eval::Value> _empty_output;

public:
    MatchesForLabelsBlueprint();
    ~MatchesForLabelsBlueprint() override;

    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

} // namespace search::features
