// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace vespalib { namespace eval { struct ConstantValue; } }

namespace search {
namespace features {

/**
 * Implements the blueprint for the constant feature.
 *
 * An executor of this outputs the value of a named constant.
 * This can either be a number or a tensor value.
 */
class ConstantBlueprint : public search::fef::Blueprint {
private:
    vespalib::string _key;  // 'foo'
    std::unique_ptr<vespalib::eval::ConstantValue> _value;

public:
    /**
     * Constructs a constant blueprint.
     */
    ConstantBlueprint();

    ~ConstantBlueprint();

    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const override;

    virtual search::fef::Blueprint::UP createInstance() const override;

    virtual search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().desc().string();
    }

    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params) override;

    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search

