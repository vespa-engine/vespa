// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

class IndexFieldInfoExecutor : public fef::FeatureExecutor
{
private:
    feature_t _type;     // from index env
    feature_t _isFilter; // from index env
    uint32_t  _fieldHandle;
    const fef::MatchData *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    IndexFieldInfoExecutor(feature_t type, feature_t isFilter,
                           uint32_t field, uint32_t fieldHandle);
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class AttrFieldInfoExecutor : public fef::FeatureExecutor
{
private:
    feature_t _type; // from index env
    uint32_t  _fieldHandle;
    const fef::MatchData *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    AttrFieldInfoExecutor(feature_t type, uint32_t fieldHandle);
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class FieldInfoBlueprint : public fef::Blueprint
{
private:
    bool      _overview;
    feature_t _indexcnt;
    feature_t _attrcnt;
    feature_t _type;
    feature_t _isFilter;
    uint32_t  _fieldId;

public:
    FieldInfoBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment &indexEnv, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override { return fef::Blueprint::UP(new FieldInfoBlueprint()); }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().
            desc(0).
            desc(1).string();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

}
