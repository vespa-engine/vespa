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

CountMatchesExecutor::CountMatchesExecutor(uint32_t fieldId,
                                 const search::fef::IQueryEnvironment &env,
                                 uint32_t begin, uint32_t end)
    : FeatureExecutor(),
      _handles()
{
    for (uint32_t i = begin; i < end; ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != search::fef::IllegalHandle) {
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
    Blueprint("countmatches"),
    _field(NULL),
    _termIdx(std::numeric_limits<uint32_t>::max())
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
    if (params.size() == 2) {
        _termIdx = params[1].asInteger();
    }
    describeOutput("out", "Returns 1 if the given field is matched by the query, 0 otherwise");
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
    if (_field == 0) {
        return search::fef::FeatureExecutor::LP(new ValueExecutor(std::vector<feature_t>(1, 0.0)));
    }
    if (_termIdx != std::numeric_limits<uint32_t>::max()) {
        return FeatureExecutor::LP(new CountMatchesExecutor(_field->id(), queryEnv, _termIdx, _termIdx + 1));
    } else {
        return FeatureExecutor::LP(new CountMatchesExecutor(_field->id(), queryEnv, 0, queryEnv.getNumTerms()));
    }
}

} // namespace features
} // namespace search
