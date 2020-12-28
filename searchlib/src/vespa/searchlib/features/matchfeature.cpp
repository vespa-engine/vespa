// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>


using namespace search::fef;
using CollectionType = FieldInfo::CollectionType;

namespace search::features {

MatchExecutor::MatchExecutor(const MatchParams & params) :
    FeatureExecutor(),
    _params(params)
{
}

void
MatchExecutor::execute(uint32_t)
{
    feature_t sum = 0.0f;
    feature_t totalWeight = 0.0f;
    for (uint32_t i = 0; i < _params.weights.size(); ++i) {
        feature_t weight = static_cast<feature_t>(_params.weights[i]);
        feature_t matchScore = inputs().get_number(i);
        if (matchScore > 0.0f) {
            totalWeight += weight;
            sum += (weight * matchScore);
        }
        outputs().set_number(i + 2, weight);
    }

    outputs().set_number(0, totalWeight > 0.0f ? sum / totalWeight : 0.0f);
    outputs().set_number(1, totalWeight);
}


MatchBlueprint::MatchBlueprint() :
    Blueprint("match"),
    _params()
{
}

MatchBlueprint::~MatchBlueprint() = default;

void
MatchBlueprint::visitDumpFeatures(const IIndexEnvironment & env,
                                  IDumpFeatureVisitor & visitor) const
{
    (void) env;
    (void) visitor;
}

Blueprint::UP
MatchBlueprint::createInstance() const
{
    return std::make_unique<MatchBlueprint>();
}

bool
MatchBlueprint::setup(const IIndexEnvironment & env,
                      const ParameterList &)
{
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const FieldInfo * info = env.getField(i);
        if (info->get_data_type() == FieldInfo::DataType::TENSOR) {
            // not matchable
            continue;
        }
        if ((info->type() == FieldType::INDEX) || (info->type() == FieldType::ATTRIBUTE)) {
            _params.weights.push_back(indexproperties::FieldWeight::lookup(env.getProperties(), info->name()));
            if (info->type() == FieldType::INDEX) {
                if (info->collection() == CollectionType::SINGLE) {
                    defineInput("fieldMatch(" + info->name() + ")");
                } else {
                    defineInput("elementCompleteness(" + info->name() + ")");
                }
            } else if (info->type() == FieldType::ATTRIBUTE) {
                defineInput("attributeMatch(" + info->name() + ")");
            }
        }
    }
    describeOutput("score", "Normalized sum over all matched fields");
    describeOutput("totalWeight", "Sum of rank weights for all matched fields");
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const FieldInfo * info = env.getField(i);
        if ((info->type() == FieldType::INDEX) || (info->type() == FieldType::ATTRIBUTE)) {
            describeOutput("weight." + info->name(), "The rank weight value for field '" + info->name() + "'");
        }
    }
    return true;
}

FeatureExecutor &
MatchBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    (void) env;
    return stash.create<MatchExecutor>(_params);
}

}
