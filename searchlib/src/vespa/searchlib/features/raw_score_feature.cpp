// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_score_feature.h"
#include "utils.h"

using namespace search::fef;

namespace search {
namespace features {

RawScoreExecutor::RawScoreExecutor(const search::fef::IQueryEnvironment &env, uint32_t fieldId)
    : FeatureExecutor(),
      _handles(),
      _md(nullptr)
{
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
    for (uint32_t i = 0; i < _handles.size(); ++i) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(_handles[i]);
        if (tfmd->getDocId() == docId) {
            output += tfmd->getRawScore();
        }
    }
    outputs().set_number(0, output);
}

void
RawScoreExecutor::handle_bind_match_data(fef::MatchData &md)
{
    _md = &md;
}

//-----------------------------------------------------------------------------

bool
RawScoreBlueprint::setup(const IIndexEnvironment &,
                         const ParameterList &params)
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

} // namespace features
} // namespace search
