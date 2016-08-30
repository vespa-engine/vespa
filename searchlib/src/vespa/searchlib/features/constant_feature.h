// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
//#include <vespa/vespalib/tensor/tensor_type.h>
//#include <vespa/vespalib/eval/value_cache/constant_value.h>

namespace vespalib { namespace eval { struct ConstantValue; } }

namespace search {
namespace features {

/**
 * Implements the blueprint for the constant feature.
 *
 * An executor of this outputs the value of a feature passed down with the constant.
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

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().string();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment &env) const;
};

} // namespace features
} // namespace search

