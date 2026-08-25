// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/blueprint.h>

#include <memory>

namespace search::features {

/**
 * Blueprint for the match bits in a given field of the terms carrying each query item label,
 * exposed as a mapped tensor<float>(label{}) with the query item labels as cell labels. A label
 * gets a cell only when it actually matches, i.e. when it is carried by at least one query term
 * searching the given field and at least one of those terms matched the document in that field.
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
