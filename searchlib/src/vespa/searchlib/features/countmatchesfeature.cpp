// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include "countmatchesfeature.h"
#include "utils.h"
#include "valuefeature.h"

#include <vespa/log/log.h>
LOG_SETUP(".features.countmatchesfeature");

using namespace search::fef;

namespace search {
namespace features {

CountMatchesExecutor::CountMatchesExecutor(uint32_t fieldId, const IQueryEnvironment &env)
    : FeatureExecutor(),
      _handles()
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != IllegalHandle) {
            _handles.push_back(handle);
        }
    }
}

void
CountMatchesExecutor::execute(MatchData &match)
{
    size_t output = 0;
    for (uint32_t i = 0; i < _handles.size(); ++i) {
        const TermFieldMatchData *tfmd = match.resolveTermField(_handles[i]);
        if (tfmd->getDocId() == match.getDocId()) {
            output++;
        }
    }
    *match.resolveFeature(outputs()[0]) = static_cast<feature_t>(output);
}


CountMatchesBlueprint::CountMatchesBlueprint() :
    Blueprint("countMatches"),
    _field(NULL)
{
}

void
CountMatchesBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

bool
CountMatchesBlueprint::setup(const IIndexEnvironment &, const ParameterList & params)
{
    _field = params[0].asField();
    describeOutput("out", "Returns number of matches in the field of all terms in the query");
    return true;
}

Blueprint::UP
CountMatchesBlueprint::createInstance() const
{
    return Blueprint::UP(new CountMatchesBlueprint());
}

FeatureExecutor::LP
CountMatchesBlueprint::createExecutor(const IQueryEnvironment & queryEnv) const
{
    if (_field == nullptr) {
        return FeatureExecutor::LP(new ValueExecutor(std::vector<feature_t>(1, 0.0)));
    }
    return FeatureExecutor::LP(new CountMatchesExecutor(_field->id(), queryEnv));
}

} // namespace features
} // namespace search
