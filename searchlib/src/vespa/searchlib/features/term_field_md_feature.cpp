// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include "term_field_md_feature.h"
#include "utils.h"
LOG_SETUP(".features.term_field_md_feature");

using namespace search::fef;

namespace search {
namespace features {


TermFieldMdExecutor::TermFieldMdExecutor(const search::fef::IQueryEnvironment &env,
                                         uint32_t fieldId)
    : _terms()
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        const search::fef::ITermData *td = env.getTerm(i);
        LOG_ASSERT(td != 0);
        const search::fef::ITermFieldData *tfd = td->lookupField(fieldId);
        if (tfd != 0) {
            LOG_ASSERT(tfd->getHandle() != search::fef::IllegalHandle);
            _terms.push_back(std::make_pair(tfd->getHandle(), td->getWeight()));
        }
    }
}

void
TermFieldMdExecutor::execute(MatchData & match)
{
    uint32_t termsmatched = 0;
    uint32_t occs = 0;
    feature_t score = 0;
    feature_t weight = 0;
    feature_t maxTermWeight = 0;

    for (size_t i = 0; i < _terms.size(); ++i) {
        const TermFieldMatchData &tfmd = *match.resolveTermField(_terms[i].first);
        int32_t termWeight = _terms[i].second.percent();

        if (tfmd.getDocId() == match.getDocId()) {
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
    *match.resolveFeature(outputs()[0]) = score;
    *match.resolveFeature(outputs()[1]) = _terms.size();
    *match.resolveFeature(outputs()[2]) = (termsmatched > 0 ? 1.0 : 0.0);
    *match.resolveFeature(outputs()[3]) = termsmatched;
    *match.resolveFeature(outputs()[4]) = weight;
    *match.resolveFeature(outputs()[5]) = occs;
    *match.resolveFeature(outputs()[6]) = maxTermWeight;
}


TermFieldMdBlueprint::TermFieldMdBlueprint() :
    Blueprint("termFieldMd"),
    _field(0)
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
    return Blueprint::UP(new TermFieldMdBlueprint());
}

bool
TermFieldMdBlueprint::setup(const IIndexEnvironment & env,
                            const ParameterList & params)
{
    _field = params[0].asField();
    LOG_ASSERT(_field != 0);

    describeOutput("score", "The term field match score");
    describeOutput("terms", "The number of ranked terms searching this field");
    describeOutput("match", "1.0 if some ranked term matched this field, 0.0 otherwise");
    describeOutput("termsmatched", "The number of ranked terms matching this field");
    describeOutput("firstweight", "The first element weight seen");
    describeOutput("occurrences", "The sum of occurrences (positions) in the match data");
    describeOutput("maxTermWeight", "The max term weight among ranked terms matching this field");

    env.hintFieldAccess(_field->id());
    return true;
}

FeatureExecutor &
TermFieldMdBlueprint::createExecutor(const IQueryEnvironment & env, vespalib::Stash &stash) const
{
    return stash.create<TermFieldMdExecutor>(env, _field->id());
}


} // namespace features
} // namespace search
