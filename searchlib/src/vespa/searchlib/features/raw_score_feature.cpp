// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_score_feature.h"
#include "utils.h"
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

RawScoreExecutor::RawScoreExecutor(const search::fef::IQueryEnvironment &env, uint32_t fieldId)
    : FeatureExecutor(),
      _handles(),
      _md(nullptr)
{
    _handles.reserve(env.getNumTerms());
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != search::fef::IllegalHandle) {
            _handles.push_back(handle);
        }
    }
}

void
RawScoreExecutor::execute(uint32_t docId)
{
    feature_t output = 0.0;
    for (auto handle : _handles) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(handle);
        if (tfmd->has_ranking_data(docId)) {
            output += tfmd->getRawScore();
        }
    }
    outputs().set_number(0, output);
}

void
RawScoreExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

//-----------------------------------------------------------------------------

RawScoreBlueprint::~RawScoreBlueprint() = default;

bool
RawScoreBlueprint::setup(const IIndexEnvironment &, const ParameterList &params)
{
    _field = params[0].asField();
    describeOutput("out", "accumulated raw score for the given field");
    return true;
}

FeatureExecutor &
RawScoreBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    return stash.create<RawScoreExecutor>(queryEnv, _field->id());
}

}
