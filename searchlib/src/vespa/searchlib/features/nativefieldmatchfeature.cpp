// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nativefieldmatchfeature.h"
#include "valuefeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

const uint32_t NativeFieldMatchParam::NOT_DEF_FIELD_LENGTH(std::numeric_limits<uint32_t>::max());

feature_t
NativeFieldMatchExecutor::calculateScore(const MyQueryTerm &qt, uint32_t docId)
{
    feature_t termScore = 0;
    for (size_t i = 0; i < qt.handles().size(); ++i) {
        TermFieldHandle tfh = qt.handles()[i];
        const TermFieldMatchData *tfmd = _md->resolveTermField(tfh);
        const NativeFieldMatchParam & param = _params.vector[tfmd->getFieldId()];
        if (tfmd->getDocId() == docId) { // do we have a hit
            FieldPositionsIterator pos = tfmd->getIterator();
            if (pos.valid()) {
                uint32_t fieldLength = getFieldLength(param, pos.getFieldLength());
                termScore +=
                    ((getFirstOccBoost(param, pos.getPosition(), fieldLength) * param.firstOccImportance) +
                     (getNumOccBoost(param, pos.size(), fieldLength) * (1 - param.firstOccImportance))) *
                    param.fieldWeight / param.maxTableSum;
            }
        }
    }
    termScore *= (qt.significance() * qt.termData()->getWeight().percent());
    return termScore;
}

NativeFieldMatchExecutor::NativeFieldMatchExecutor(const IQueryEnvironment & env,
                                                   const NativeFieldMatchParams & params) :
    FeatureExecutor(),
    _params(params),
    _queryTerms(),
    _divisor(0),
    _md(nullptr)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        MyQueryTerm qt(QueryTermFactory::create(env, i));
        if (qt.termData()->getWeight().percent() != 0) // only consider query terms with contribution
        {
            typedef search::fef::ITermFieldRangeAdapter FRA;
            uint32_t totalFieldWeight = 0;
            for (FRA iter(*qt.termData()); iter.valid(); iter.next()) {
                const ITermFieldData& tfd = iter.get();
                uint32_t fieldId = tfd.getFieldId();
                if (_params.considerField(fieldId)) { // only consider fields with contribution
                    totalFieldWeight += _params.vector[fieldId].fieldWeight;
                    qt.handles().push_back(tfd.getHandle());
                }
            }
            if (!qt.handles().empty()) {
                _queryTerms.push_back(qt);
                _divisor += (qt.significance() * qt.termData()->getWeight().percent() * totalFieldWeight);
            }
        }
    }
}

void
NativeFieldMatchExecutor::execute(uint32_t docId)
{
    feature_t score = 0;
    for (size_t i = 0; i < _queryTerms.size(); ++i) {
        score += calculateScore(_queryTerms[i], docId);
    }
    if (_divisor > 0) {
        score /= _divisor;
    }
    outputs().set_number(0, score);
}

void
NativeFieldMatchExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

NativeFieldMatchBlueprint::NativeFieldMatchBlueprint() :
    Blueprint("nativeFieldMatch"),
    _params(),
    _defaultFirstOcc("expdecay(8000,12.50)"),
    _defaultNumOcc("loggrowth(1500,4000,19)")
{
}

NativeFieldMatchBlueprint::~NativeFieldMatchBlueprint() = default;

void
NativeFieldMatchBlueprint::visitDumpFeatures(const IIndexEnvironment & env,
                                             IDumpFeatureVisitor & visitor) const
{
    (void) env;
    visitor.visitDumpFeature(getBaseName());
}

Blueprint::UP
NativeFieldMatchBlueprint::createInstance() const
{
    return std::make_unique<NativeFieldMatchBlueprint>();
}

bool
NativeFieldMatchBlueprint::setup(const IIndexEnvironment & env,
                                 const ParameterList & params)
{
    _params.resize(env.getNumFields());
    FieldWrapper fields(env, params, FieldType::INDEX);
    vespalib::string defaultFirstOccImportance = env.getProperties().lookup(getBaseName(), "firstOccurrenceImportance").get("0.5");
    for (uint32_t i = 0; i < fields.getNumFields(); ++i) {
        const FieldInfo * info = fields.getField(i);
        uint32_t fieldId = info->id();
        NativeFieldMatchParam & param = _params.vector[fieldId];
        param.field = true;
        if ((param.firstOccTable =
             util::lookupTable(env, getBaseName(), "firstOccurrenceTable", info->name(), _defaultFirstOcc)) == NULL)
        {
            return false;
        }
        if ((param.numOccTable =
             util::lookupTable(env, getBaseName(), "occurrenceCountTable", info->name(), _defaultNumOcc)) == NULL)
        {
            return false;
        }
        param.fieldWeight = indexproperties::FieldWeight::lookup(env.getProperties(), info->name());
        if (param.fieldWeight == 0 ||
            info->isFilter())
        {
            param.field = false;
        }
        Property afl = env.getProperties().lookup(getBaseName(), "averageFieldLength", info->name());
        if (afl.found()) {
            param.averageFieldLength = util::strToNum<uint32_t>(afl.get());
        }

        param.firstOccImportance = util::strToNum<feature_t>
            (env.getProperties().lookup(getBaseName(), "firstOccurrenceImportance", info->name()).
             get(defaultFirstOccImportance));

        if (NativeRankBlueprint::useTableNormalization(env)) {
            const Table * fo = param.firstOccTable;
            const Table * no = param.numOccTable;
            if (fo != NULL && no != NULL) {
                double value = (fo->max() * param.firstOccImportance) +
                    (no->max() * (1 - param.firstOccImportance));
                _params.setMaxTableSums(fieldId, value);
            }
        }
        if (param.field) {
            env.hintFieldAccess(fieldId);
        }
    }
    _params.minFieldLength = util::strToNum<uint32_t>(env.getProperties().lookup
                                                      (getBaseName(), "minFieldLength").get("6"));

    describeOutput("score", "The native field match score");
    return true;
}

FeatureExecutor &
NativeFieldMatchBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    NativeFieldMatchExecutor &native = stash.create<NativeFieldMatchExecutor>(env, _params);
    if (native.empty()) {
        return stash.create<SingleZeroValueExecutor>();
    } else {
        return native;
    }
}

}
