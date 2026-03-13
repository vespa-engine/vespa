// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchcountfeature.h"
#include "utils.h"
#include "valuefeature.h"
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

namespace {

/**
* Implements the executor for the matchCount feature for index and
* attribute fields.
*/
class MatchCountExecutor : public fef::FeatureExecutor {
private:
    std::vector<fef::TermFieldHandle> _handles;
    const fef::MatchData *_md;

    void handle_bind_match_data(const fef::MatchData &md) override {
        _md = &md;
    }

public:
    MatchCountExecutor(uint32_t fieldId, const fef::IQueryEnvironment &env);

    void execute(uint32_t docId) override;
};

MatchCountExecutor::MatchCountExecutor(uint32_t fieldId, const IQueryEnvironment &env)
    : FeatureExecutor(),
      _handles(),
      _md(nullptr)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        TermFieldHandle handle = util::getTermFieldHandle(env, i, fieldId);
        if (handle != IllegalHandle) {
            _handles.push_back(handle);
        }
    }
}

void
MatchCountExecutor::execute(uint32_t docId) {
    size_t output = 0;
    for (uint32_t i = 0; i < _handles.size(); ++i) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(_handles[i]);
        if (tfmd->has_ranking_data(docId)) {
            output++;
        }
    }
    outputs().set_number(0, static_cast<feature_t>(output));
}

}

MatchCountBlueprint::MatchCountBlueprint() :
    Blueprint("matchCount"),
    _field(nullptr)
{
}

void
MatchCountBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

bool
MatchCountBlueprint::setup(const IIndexEnvironment &, const ParameterList & params)
{
    _field = params[0].asField();
    describeOutput("out", "Returns number of matches in the field of all terms in the query");
    return true;
}

Blueprint::UP
MatchCountBlueprint::createInstance() const
{
    return std::make_unique<MatchCountBlueprint>();
}

FeatureExecutor &
MatchCountBlueprint::createExecutor(const IQueryEnvironment & queryEnv, vespalib::Stash &stash) const
{
    if (_field == nullptr) {
        return stash.create<SingleZeroValueExecutor>();
    }
    return stash.create<MatchCountExecutor>(_field->id(), queryEnv);
}

}
