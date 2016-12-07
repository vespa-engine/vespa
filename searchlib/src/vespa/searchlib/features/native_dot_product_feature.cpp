// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.native_dot_product_feature");
#include "native_dot_product_feature.h"
#include "utils.h"

using namespace search::fef;

namespace search {
namespace features {

NativeDotProductExecutor::NativeDotProductExecutor(const search::fef::IQueryEnvironment &env, uint32_t fieldId)
    : FeatureExecutor(),
      _pairs()
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != search::fef::IllegalHandle) {
            _pairs.push_back(std::make_pair(handle, env.getTerm(i)->getWeight()));
        }
    }
}

void
NativeDotProductExecutor::execute(MatchData &data)
{
    feature_t output = 0.0;
    for (uint32_t i = 0; i < _pairs.size(); ++i) {
        const TermFieldMatchData *tfmd = data.resolveTermField(_pairs[i].first);
        if (tfmd->getDocId() == data.getDocId()) {
            output += (tfmd->getWeight() * (int32_t)_pairs[i].second.percent());
        }
    }
    outputs().set_number(0, output);
}

//-----------------------------------------------------------------------------

bool
NativeDotProductBlueprint::setup(const IIndexEnvironment &,
                                 const ParameterList &params)
{
    _field = params[0].asField();
    describeOutput("out", "dot product between query term weights and match weights for the given field");
    return true;
}

FeatureExecutor &
NativeDotProductBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    return stash.create<NativeDotProductExecutor>(queryEnv, _field->id());
}

} // namespace features
} // namespace search
