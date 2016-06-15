// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.subqueries_feature");
#include "subqueries_feature.h"
#include "utils.h"

using namespace search::fef;

namespace search {
namespace features {

SubqueriesExecutor::SubqueriesExecutor(const IQueryEnvironment &env,
                                       uint32_t fieldId)
    : FeatureExecutor(),
      _handles() {
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != IllegalHandle) {
            _handles.push_back(handle);
        }
    }
}

void SubqueriesExecutor::execute(MatchData &data) {
    uint32_t lsb = 0;
    uint32_t msb = 0;
    for (uint32_t i = 0; i < _handles.size(); ++i) {
        const TermFieldMatchData *tfmd = data.resolveTermField(_handles[i]);
        if (tfmd->getDocId() == data.getDocId()) {
            lsb |= static_cast<uint32_t>(tfmd->getSubqueries());
            msb |= tfmd->getSubqueries() >> 32;
        }
    }
    *data.resolveFeature(outputs()[0]) = lsb;
    *data.resolveFeature(outputs()[1]) = msb;
}

//-----------------------------------------------------------------------------

bool SubqueriesBlueprint::setup(const IIndexEnvironment &,
                                const ParameterList &params) {
    _field = params[0].asField();
    describeOutput("lsb", "32 least significant bits of the subquery bitmap"
                   " for the given field");
    describeOutput("msb", "32 most significant bits of the subquery bitmap"
                   " for the given field");
    return true;
}

FeatureExecutor::LP
SubqueriesBlueprint::createExecutor(const IQueryEnvironment &queryEnv) const {
    return FeatureExecutor::LP(new SubqueriesExecutor(queryEnv, _field->id()));
}

} // namespace features
} // namespace search
