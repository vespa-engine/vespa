// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/** Returns distinct matched bindings as a mapped tensor with one dimension per requested variable. */
class CorrelatedBindingsBlueprint : public fef::Blueprint {
    std::string _label;
    vespalib::eval::ValueType _value_type;

public:
    CorrelatedBindingsBlueprint();
    ~CorrelatedBindingsBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment&, fef::IDumpFeatureVisitor&) const override {}
    fef::Blueprint::UP createInstance() const override { return std::make_unique<CorrelatedBindingsBlueprint>(); }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string().string().string().repeat();
    }
    bool setup(const fef::IIndexEnvironment&, const fef::ParameterList& params) override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

} // namespace search::features
