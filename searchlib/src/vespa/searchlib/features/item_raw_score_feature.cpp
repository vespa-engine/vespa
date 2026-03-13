// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "item_raw_score_feature.h"
#include "valuefeature.h"
#include "utils.h"
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

void
ItemRawScoreExecutor::execute(uint32_t docId)
{
    feature_t output = 0.0;
    for (uint32_t i = 0; i < _handles.size(); ++i) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(_handles[i]);
        if (tfmd->has_ranking_data(docId)) {
            output += tfmd->getRawScore();
        }
    }
    outputs().set_number(0, output);
}

void
ItemRawScoreExecutor::handle_bind_match_data(const MatchData &md)
{
    _md = &md;
}

//-----------------------------------------------------------------------------

void
SimpleItemRawScoreExecutor::execute(uint32_t docId)
{
    feature_t output = 0.0;
    const TermFieldMatchData *tfmd = _md->resolveTermField(_handle);
    if (tfmd->has_ranking_data(docId)) {
        output = tfmd->getRawScore();
    }
    outputs().set_number(0, output);
}

void
SimpleItemRawScoreExecutor::handle_bind_match_data(const MatchData &md)
{
    _md = &md;
}

//-----------------------------------------------------------------------------

ItemRawScoreBlueprint::~ItemRawScoreBlueprint() = default;

bool
ItemRawScoreBlueprint::setup(const IIndexEnvironment &,
                             const ParameterList &params)
{
    _label = params[0].getValue();
    describeOutput("out", "raw score for the given query item");
    return true;
}

FeatureExecutor &
ItemRawScoreBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    HandleVector handles = resolve(queryEnv, _label);
    if (handles.size() == 1) {
        return stash.create<SimpleItemRawScoreExecutor>(handles[0]);
    } else if (handles.size() == 0) {
        return stash.create<SingleZeroValueExecutor>();
    } else {        
        return stash.create<ItemRawScoreExecutor>(handles);
    }
}

ItemRawScoreBlueprint::HandleVector
ItemRawScoreBlueprint::resolve(const IQueryEnvironment &env,
                               const std::string &label)
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

}
