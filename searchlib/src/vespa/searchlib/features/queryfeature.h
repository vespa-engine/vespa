// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/query_value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>

namespace search::features {

/**
 * Implements the blueprint for the query feature.
 *
 * An executor of this outputs the value of a feature passed down with the query.
 * This can either be a number or a tensor value.
 */
class QueryBlueprint : public fef::Blueprint {
private:
    search::fef::QueryValue _qvalue;
    vespalib::eval::Value::UP _default_object_value;

public:
    QueryBlueprint();
    ~QueryBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    void prepareSharedState(const fef::IQueryEnvironment &env, fef::IObjectStore &store) const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
