// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/eval/eval/value_type.h>

namespace search {
namespace features {

/**
 * Implements the blueprint for the query feature.
 *
 * An executor of this outputs the value of a feature passed down with the query.
 * This can either be a number or a tensor value.
 */
class QueryBlueprint : public search::fef::Blueprint {
private:
    vespalib::string _key;  // 'foo'
    vespalib::string _key2; // '$foo'
    feature_t _defaultValue;
    vespalib::eval::ValueType _valueType;

public:
    /**
     * Constructs a query blueprint.
     */
    QueryBlueprint();

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
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search

