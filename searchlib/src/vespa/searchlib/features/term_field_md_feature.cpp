// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "term_field_md_feature.h"
#include "utils.h"
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>
#include <cassert>

using namespace search::fef;

namespace search::features {

TermFieldMdExecutor::TermFieldMdExecutor(const fef::IQueryEnvironment &env, uint32_t fieldId)
    : _terms(),
      _md(nullptr)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        const fef::ITermData *td = env.getTerm(i);
        assert(td != nullptr);
        const fef::ITermFieldData *tfd = td->lookupField(fieldId);
        if (tfd != nullptr) {
            assert(tfd->getHandle() != fef::IllegalHandle);
            _terms.push_back(std::make_pair(tfd->getHandle(), td->getWeight()));
        }
    }
}

void
TermFieldMdExecutor::execute(uint32_t docId)
{
    uint32_t termsmatched = 0;
    uint32_t occs = 0;
    feature_t score = 0;
    feature_t weight = 0;
    feature_t maxTermWeight = 0;

    for (size_t i = 0; i < _terms.size(); ++i) {
        const TermFieldMatchData &tfmd = *_md->resolveTermField(_terms[i].first);
        int32_t termWeight = _terms[i].second.percent();

        if (tfmd.has_ranking_data(docId)) {
            ++termsmatched;
            score += tfmd.getWeight();
            occs += (tfmd.end() - tfmd.begin());
            if (weight == 0) {
                weight = tfmd.getWeight();
            }
            if (termWeight > maxTermWeight) {
                maxTermWeight = termWeight;
            }
        }

    }
    outputs().set_number(0, score);
    outputs().set_number(1, _terms.size());
    outputs().set_number(2, (termsmatched > 0 ? 1.0 : 0.0));
    outputs().set_number(3, termsmatched);
    outputs().set_number(4, weight);
    outputs().set_number(5, occs);
    outputs().set_number(6, maxTermWeight);
}

void
TermFieldMdExecutor::handle_bind_match_data(const MatchData &md)
{
    _md = &md;
}

TermFieldMdBlueprint::TermFieldMdBlueprint() :
    Blueprint("termFieldMd"),
    _field(nullptr)
{
}

void
TermFieldMdBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                        IDumpFeatureVisitor &) const
{
}

Blueprint::UP
TermFieldMdBlueprint::createInstance() const
{
    return std::make_unique<TermFieldMdBlueprint>();
}

bool
TermFieldMdBlueprint::setup(const IIndexEnvironment &,
                            const ParameterList & params)
{
    _field = params[0].asField();
    assert(_field != nullptr);

    describeOutput("score", "The term field match score");
    describeOutput("terms", "The number of ranked terms searching this field");
    describeOutput("match", "1.0 if some ranked term matched this field, 0.0 otherwise");
    describeOutput("termsmatched", "The number of ranked terms matching this field");
    describeOutput("firstweight", "The first element weight seen");
    describeOutput("occurrences", "The sum of occurrences (positions) in the match data");
    describeOutput("maxTermWeight", "The max term weight among ranked terms matching this field");

    return true;
}

FeatureExecutor &
TermFieldMdBlueprint::createExecutor(const IQueryEnvironment & env, vespalib::Stash &stash) const
{
    return stash.create<TermFieldMdExecutor>(env, _field->id());
}

}
