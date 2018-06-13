// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/query/weight.h>
#include <algorithm>

namespace search {
namespace features {

class NativeDotProductExecutor : public fef::FeatureExecutor
{
private:
    typedef std::pair<fef::TermFieldHandle,query::Weight> Pair;
    std::vector<Pair>     _pairs;
    const fef::MatchData *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    NativeDotProductExecutor(const fef::IQueryEnvironment &env);
    NativeDotProductExecutor(const fef::IQueryEnvironment &env, uint32_t fieldId);
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class NativeDotProductBlueprint : public fef::Blueprint
{
private:
    const fef::FieldInfo *_field;
public:
    NativeDotProductBlueprint() : Blueprint("nativeDotProduct"), _field(nullptr) {}
    void visitDumpFeatures(const fef::IIndexEnvironment &, fef::IDumpFeatureVisitor &) const override {}
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new NativeDotProductBlueprint());
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field().desc();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search

