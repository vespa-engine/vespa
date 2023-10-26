// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace vespalib { namespace eval { struct ConstantValue; } }

namespace search::features {

/**
 * Implements the blueprint for the constant feature.
 *
 * An executor of this outputs the value of a named constant.
 * This can either be a number or a tensor value.
 */
class ConstantBlueprint : public fef::Blueprint {
private:
    vespalib::string _key;  // 'foo'
    std::unique_ptr<vespalib::eval::ConstantValue> _value;

public:
    /**
     * Constructs a constant blueprint.
     */
    ConstantBlueprint();

    ~ConstantBlueprint() override;

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;

    fef::Blueprint::UP createInstance() const override;

    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string();
    }

    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;

    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
