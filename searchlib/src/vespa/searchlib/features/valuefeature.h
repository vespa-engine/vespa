// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vector>

namespace search::features {

class ValueExecutor final : public fef::FeatureExecutor
{
private:
    std::vector<feature_t> _values;

public:
    ValueExecutor(const std::vector<feature_t> & values);
    bool isPure() override { return true; }
    void execute(uint32_t docId) override;
    const std::vector<feature_t> & getValues() const { return _values; }
};

class SingleValueExecutor final : public fef::FeatureExecutor
{
private:
    feature_t _value;

public:
    SingleValueExecutor(feature_t value) : _value(value) { }
    bool isPure() override { return true; }
    void execute(uint32_t docId) override;
};

class SingleZeroValueExecutor final : public fef::FeatureExecutor
{
public:
    SingleZeroValueExecutor() : FeatureExecutor() {}
    bool isPure() override { return true; }
    void execute(uint32_t docId) override;
};


class ValueBlueprint : public fef::Blueprint
{
private:
    std::vector<feature_t> _values;

public:
    ValueBlueprint();
    ~ValueBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment & indexEnv,
                           fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override { return Blueprint::UP(new ValueBlueprint()); }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().number().number().repeat();
    }
    bool setup(const fef::IIndexEnvironment & env,
               const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

}
