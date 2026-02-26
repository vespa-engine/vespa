// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "native_dot_product_feature.h"
#include "utils.h"
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

NativeDotProductExecutor::NativeDotProductExecutor(const search::fef::IQueryEnvironment &env)
    : FeatureExecutor(),
      _pairs(),
      _md(nullptr)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        const search::fef::ITermData *td = env.getTerm(i);
        auto weight = td->getWeight();
        for (size_t f = 0; f < td->numFields(); ++f) {
            auto handle = td->field(f).getHandle();
            if (handle != search::fef::IllegalHandle) {
                _pairs.emplace_back(handle, weight);
            }
        }
    }
}

NativeDotProductExecutor::NativeDotProductExecutor(const search::fef::IQueryEnvironment &env, uint32_t fieldId)
    : FeatureExecutor(),
      _pairs(),
      _md(nullptr)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != search::fef::IllegalHandle) {
            _pairs.push_back(std::make_pair(handle, env.getTerm(i)->getWeight()));
        }
    }
}

void
NativeDotProductExecutor::execute(uint32_t docId)
{
    feature_t output = 0.0;
    for (uint32_t i = 0; i < _pairs.size(); ++i) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(_pairs[i].first);
        if (tfmd->has_ranking_data(docId)) {
            output += (tfmd->getWeight() * (int32_t)_pairs[i].second.percent());
        }
    }
    outputs().set_number(0, output);
}

void
NativeDotProductExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

//-----------------------------------------------------------------------------

NativeDotProductBlueprint::~NativeDotProductBlueprint() = default;

bool
NativeDotProductBlueprint::setup(const IIndexEnvironment &,
                                 const ParameterList &params)
{
    if (params.size() > 0) {
        _field = params[0].asField();
    }
    describeOutput("out", "dot product between query term weights and match weights");
    return true;
}

FeatureExecutor &
NativeDotProductBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    if (_field) {
        return stash.create<NativeDotProductExecutor>(queryEnv, _field->id());
    } else {
        return stash.create<NativeDotProductExecutor>(queryEnv);
    }
}

}
