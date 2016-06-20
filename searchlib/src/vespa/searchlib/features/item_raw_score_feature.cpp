// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.item_raw_score_feature");
#include "item_raw_score_feature.h"
#include "valuefeature.h"
#include "utils.h"

using namespace search::fef;

namespace search {
namespace features {

void
ItemRawScoreExecutor::execute(MatchData &data)
{
    feature_t output = 0.0;
    for (uint32_t i = 0; i < _handles.size(); ++i) {
        const TermFieldMatchData *tfmd = data.resolveTermField(_handles[i]);
        if (tfmd->getDocId() == data.getDocId()) {
            output += tfmd->getRawScore();
        }
    }
    *data.resolveFeature(outputs()[0]) = output;
}

//-----------------------------------------------------------------------------

void
SimpleItemRawScoreExecutor::execute(MatchData &data)
{
    feature_t output = 0.0;
    const TermFieldMatchData *tfmd = data.resolveTermField(_handle);
    if (tfmd->getDocId() == data.getDocId()) {
        output = tfmd->getRawScore();
    }
    *data.resolveFeature(outputs()[0]) = output;
}

//-----------------------------------------------------------------------------

bool
ItemRawScoreBlueprint::setup(const IIndexEnvironment &,
                             const ParameterList &params)
{
    _label = params[0].getValue();
    describeOutput("out", "raw score for the given query item");
    return true;
}

FeatureExecutor::LP
ItemRawScoreBlueprint::createExecutor(const IQueryEnvironment &queryEnv) const
{
    HandleVector handles = resolve(queryEnv, _label);
    if (handles.size() == 1) {
        return FeatureExecutor::LP(new SimpleItemRawScoreExecutor(handles[0]));
    } else if (handles.size() == 0) {
        return FeatureExecutor::LP(new SingleZeroValueExecutor());
    } else {        
        return FeatureExecutor::LP(new ItemRawScoreExecutor(handles));
    }
}

ItemRawScoreBlueprint::HandleVector
ItemRawScoreBlueprint::resolve(const search::fef::IQueryEnvironment &env,
                               const vespalib::string &label)
{
    HandleVector handles;
    const ITermData *term = util::getTermByLabel(env, label);
    if (term != nullptr) {
        uint32_t numFields(term->numFields());
        for (uint32_t i(0); i < numFields; ++i) {
            TermFieldHandle handle = term->field(i).getHandle();
            if (handle != IllegalHandle) {
                handles.push_back(handle);
            }
        }
    }
    return handles;
}


} // namespace features
} // namespace search
