// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include "valuefeature.h"
#include "nativeattributematchfeature.h"
#include "utils.h"
LOG_SETUP(".features.nativeattributematchfeature");

using namespace search::fef;

namespace search {
namespace features {

feature_t
NativeAttributeMatchExecutor::calculateScore(const CachedTermData &td, const TermFieldMatchData &tfmd)
{
    return (td.weightBoostTable->get(tfmd.getWeight()) * td.scale);
}

NativeAttributeMatchExecutor::Precomputed
NativeAttributeMatchExecutor::preComputeSetup(const IQueryEnvironment & env,
                                              const NativeAttributeMatchParams & params)
{
    NativeAttributeMatchExecutor::Precomputed precomputed;
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        const ITermData *termData = env.getTerm(i);
        if (termData->getWeight().percent() != 0) // only consider query terms with contribution
        {
            typedef search::fef::ITermFieldRangeAdapter FRA;
            for (FRA iter(*termData); iter.valid(); iter.next()) {
                const ITermFieldData& tfd = iter.get();
                uint32_t fieldId = tfd.getFieldId();
                if (params.considerField(fieldId)) { // only consider fields with contribution
                    const NativeAttributeMatchParams::Param & param = params.vector[fieldId];
                    precomputed.first.push_back(CachedTermData(params, tfd,
                                                        param.fieldWeight * termData->getWeight().percent() / param.maxTableSum));
                    precomputed.second += (param.fieldWeight * termData->getWeight().percent());
                }
            }
        }
    }
    return precomputed;
}

FeatureExecutor::LP
NativeAttributeMatchExecutor::createExecutor(const IQueryEnvironment & env,
                                             const NativeAttributeMatchParams & params)
{
    Precomputed setup = preComputeSetup(env, params);
    if (setup.first.size() == 0) {
        return LP(new ValueExecutor(std::vector<feature_t>(1, 0.0)));
    } else if (setup.first.size() == 1) {
        return LP(new NativeAttributeMatchExecutorSingle(setup));
    } else {
        return LP(new NativeAttributeMatchExecutorMulti(setup));
    }
}

void
NativeAttributeMatchExecutorMulti::execute(MatchData & match)
{
    feature_t score = 0;
    for (size_t i = 0; i < _queryTermData.size(); ++i) {
        const TermFieldMatchData *tfmd = match.resolveTermField(_queryTermData[i].tfh);
        if (tfmd->getDocId() == match.getDocId()) {
            score += calculateScore(_queryTermData[i], *tfmd);
        }
    }
    *match.resolveFeature(outputs()[0]) = score / _divisor;
}

void
NativeAttributeMatchExecutorSingle::execute(MatchData & match)
{
    const TermFieldMatchData &tfmd = *match.resolveTermField(_queryTermData.tfh);
    *match.resolveFeature(outputs()[0]) = (tfmd.getDocId() == match.getDocId())
                                          ? calculateScore(_queryTermData, tfmd)
                                          : 0;
}


NativeAttributeMatchBlueprint::NativeAttributeMatchBlueprint() :
    Blueprint("nativeAttributeMatch"),
    _params()
{
}

namespace {
const vespalib::string DefaultWeightTable = "linear(1,0)";
const vespalib::string WeightTableName = "weightTable";
}

void
NativeAttributeMatchBlueprint::visitDumpFeatures(const IIndexEnvironment & env,
                                                 IDumpFeatureVisitor & visitor) const
{
    (void) env;
    visitor.visitDumpFeature(getBaseName());
}

Blueprint::UP
NativeAttributeMatchBlueprint::createInstance() const
{
    return Blueprint::UP(new NativeAttributeMatchBlueprint());
}

bool
NativeAttributeMatchBlueprint::setup(const IIndexEnvironment & env,
                                     const ParameterList & params)
{
    _params.resize(env.getNumFields());
    FieldWrapper fields(env, params, FieldType::ATTRIBUTE);
    for (uint32_t i = 0; i < fields.getNumFields(); ++i) {
        const FieldInfo * info = fields.getField(i);

        uint32_t fieldId = info->id();
        NativeAttributeMatchParams::Param & param = _params.vector[fieldId];
        param.field = true;
        const Table * weightBoostTable = util::lookupTable(env, getBaseName(), WeightTableName, info->name(), DefaultWeightTable);
        if (weightBoostTable == NULL) {
            return false;
        }
        param.weightBoostTable = SymmetricTable(*weightBoostTable);
        param.fieldWeight = indexproperties::FieldWeight::lookup(env.getProperties(), info->name());
        if (param.fieldWeight == 0) {
            param.field = false;
        }
        if (NativeRankBlueprint::useTableNormalization(env)) {
            _params.setMaxTableSums(fieldId, param.weightBoostTable.max());
        }
    }

    describeOutput("score", "The native attribute match score");
    return true;
}

FeatureExecutor::LP
NativeAttributeMatchBlueprint::createExecutor(const IQueryEnvironment & env) const
{
    return FeatureExecutor::LP(NativeAttributeMatchExecutor::createExecutor(env, _params));
}


} // namespace features
} // namespace search
