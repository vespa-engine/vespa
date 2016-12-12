// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/common/feature.h>

namespace search {
namespace features {

class IndexFieldInfoExecutor : public search::fef::FeatureExecutor
{
private:
    feature_t _type;     // from index env
    feature_t _isFilter; // from index env
    uint32_t  _field;
    uint32_t  _fieldHandle;
    const fef::MatchData *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    IndexFieldInfoExecutor(feature_t type, feature_t isFilter,
                           uint32_t field, uint32_t fieldHandle);
    virtual void execute(uint32_t docId);
};

//-----------------------------------------------------------------------------

class AttrFieldInfoExecutor : public search::fef::FeatureExecutor
{
private:
    feature_t _type; // from index env
    uint32_t  _fieldHandle;
    const fef::MatchData *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    AttrFieldInfoExecutor(feature_t type, uint32_t fieldHandle);
    virtual void execute(uint32_t docId);
};

//-----------------------------------------------------------------------------

class FieldInfoBlueprint : public search::fef::Blueprint
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
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &indexEnv,
                                   search::fef::IDumpFeatureVisitor &visitor) const;
    virtual search::fef::Blueprint::UP createInstance() const { return search::fef::Blueprint::UP(new FieldInfoBlueprint()); }
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().
            desc(0).
            desc(1).string();
    }
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search

