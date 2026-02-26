// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nativeattributematchfeature.h"
#include "valuefeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

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
            using FRA = search::fef::ITermFieldRangeAdapter;
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

FeatureExecutor &
NativeAttributeMatchExecutor::createExecutor(const IQueryEnvironment & env,
                                             const NativeAttributeMatchParams & params,
                                             vespalib::Stash &stash)
{
    Precomputed setup = preComputeSetup(env, params);
    if (setup.first.size() == 0) {
        return stash.create<SingleZeroValueExecutor>();
    } else if (setup.first.size() == 1) {
        return stash.create<NativeAttributeMatchExecutorSingle>(setup);
    } else {
        return stash.create<NativeAttributeMatchExecutorMulti>(setup);
    }
}

void
NativeAttributeMatchExecutorMulti::execute(uint32_t docId)
{
    feature_t score = 0;
    for (size_t i = 0; i < _queryTermData.size(); ++i) {
        const TermFieldMatchData *tfmd = _md->resolveTermField(_queryTermData[i].tfh);
        if (tfmd->has_ranking_data(docId)) {
            score += calculateScore(_queryTermData[i], *tfmd);
        }
    }
    outputs().set_number(0, score / _divisor);
}

void
NativeAttributeMatchExecutorMulti::handle_bind_match_data(const MatchData &md)
{
    _md = &md;
}

void
NativeAttributeMatchExecutorSingle::execute(uint32_t docId)
{
    const TermFieldMatchData &tfmd = *_md->resolveTermField(_queryTermData.tfh);
    outputs().set_number(0, tfmd.has_ranking_data(docId)
                         ? calculateScore(_queryTermData, tfmd)
                         : 0);
}

void
NativeAttributeMatchExecutorSingle::handle_bind_match_data(const MatchData &md)
{
    _md = &md;
}

NativeAttributeMatchBlueprint::NativeAttributeMatchBlueprint()
    : Blueprint("nativeAttributeMatch"),
      _params()
{
}

NativeAttributeMatchBlueprint::~NativeAttributeMatchBlueprint() = default;

namespace {
const std::string DefaultWeightTable = "linear(1,0)";
const std::string WeightTableName = "weightTable";
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
    return std::make_unique<NativeAttributeMatchBlueprint>();
}

fef::ParameterDescriptions
NativeAttributeMatchBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().attribute(fef::ParameterDataTypeSet::primitiveTypeSet(), fef::ParameterCollection::ANY).repeat();
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
        if (weightBoostTable == nullptr) {
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

FeatureExecutor &
NativeAttributeMatchBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return NativeAttributeMatchExecutor::createExecutor(env, _params, stash);
}

}
