// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/common/feature.h>

namespace search {
namespace features {

class ValueExecutor : public search::fef::FeatureExecutor
{
private:
    std::vector<feature_t> _values;

public:
    ValueExecutor(const std::vector<feature_t> & values);
    virtual bool isPure() { return true; }
    virtual void execute(search::fef::MatchData & data);
    const std::vector<feature_t> & getValues() const { return _values; }
};

class SingleZeroValueExecutor : public search::fef::FeatureExecutor
{
public:
    SingleZeroValueExecutor() : FeatureExecutor() {}
    virtual bool isPure() { return true; }
    virtual void execute(search::fef::MatchData & data);
};


class ValueBlueprint : public search::fef::Blueprint
{
private:
    std::vector<feature_t> _values;

public:
    ValueBlueprint();

    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & indexEnv,
                                   search::fef::IDumpFeatureVisitor & visitor) const;
    virtual search::fef::Blueprint::UP createInstance() const { return Blueprint::UP(new ValueBlueprint()); }
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().number().number().repeat();
    }
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment & queryEnv) const override {
        (void) queryEnv;
        return search::fef::FeatureExecutor::LP(new ValueExecutor(_values));
    }
};

} // namespace features
} // namespace search

