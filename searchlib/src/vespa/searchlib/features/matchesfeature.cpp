// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchesfeature.h"
#include "utils.h"
#include "valuefeature.h"
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/vespalib/util/stash.h>


using namespace search::fef;

namespace search::features {

MatchesExecutor::MatchesExecutor(uint32_t fieldId,
                                 const search::fef::IQueryEnvironment &env,
                                 uint32_t begin, uint32_t end)
    : FeatureExecutor(),
      _handles(),
      _md(nullptr)
{
    for (uint32_t i = begin; i < end; ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != search::fef::IllegalHandle) {
            _handles.push_back(handle);
        }
    }
}

void
MatchesExecutor::execute(uint32_t docId)
{
    size_t output = 0;
    for (uint32_t i = 0; i < _handles.size(); ++i) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(_handles[i]);
        if (tfmd->getDocId() == docId) {
            output = 1;
            break;
        }
    }
    outputs().set_number(0, static_cast<feature_t>(output));
}

void
MatchesExecutor::handle_bind_match_data(const MatchData &md)
{
    _md = &md;
}

MatchesBlueprint::MatchesBlueprint() :
    Blueprint("matches"),
    _field(nullptr),
    _termIdx(std::numeric_limits<uint32_t>::max())
{
}

void
MatchesBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                    IDumpFeatureVisitor &) const
{
}

bool
MatchesBlueprint::setup(const IIndexEnvironment &,
                        const ParameterList & params)
{
    _field = params[0].asField();
    if (params.size() == 2) {
        _termIdx = params[1].asInteger();
    }
    describeOutput("out", "Returns 1 if the given field is matched by the query, 0 otherwise");
    return true;
}

Blueprint::UP
MatchesBlueprint::createInstance() const
{
    return std::make_unique<MatchesBlueprint>();
}

FeatureExecutor &
MatchesBlueprint::createExecutor(const IQueryEnvironment & queryEnv, vespalib::Stash &stash) const
{
    if (_field == 0) {
        return stash.create<ValueExecutor>(std::vector<feature_t>(1, 0.0));
    }
    if (_termIdx != std::numeric_limits<uint32_t>::max()) {
        return stash.create<MatchesExecutor>(_field->id(), queryEnv, _termIdx, _termIdx + 1);
    } else {
        return stash.create<MatchesExecutor>(_field->id(), queryEnv, 0, queryEnv.getNumTerms());
    }
}

}
