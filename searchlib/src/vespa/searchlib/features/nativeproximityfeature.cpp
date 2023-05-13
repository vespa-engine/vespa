// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nativeproximityfeature.h"
#include "valuefeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

NativeProximityExecutorSharedState::NativeProximityExecutorSharedState(const IQueryEnvironment& env,
                                                                       const NativeProximityParams& params)
    : fef::Anything(),
      _params(params),
      _setups(),
      _total_field_weight(0),
      _fields()
{
    QueryTermHelper queryTerms(env);
    for (const QueryTerm& qt : queryTerms.terms()) {
        using FRA = search::fef::ITermFieldRangeAdapter;
        for (FRA iter(*qt.termData()); iter.valid(); iter.next()) {
            uint32_t fieldId = iter.get().getFieldId();
            if (_params.considerField(fieldId)) { // only consider fields with contribution
                QueryTerm myQt = qt;
                myQt.fieldHandle(iter.get().getHandle());
                _fields[fieldId].push_back(myQt);
            }
        }
    }
    for (const auto& entry : _fields) {
        if (entry.second.size() >= 2) {
            FieldSetup setup(entry.first);
            generateTermPairs(env, entry.second, _params.slidingWindow, setup);
            if (!setup.pairs.empty()) {
                _setups.push_back(std::move(setup));
                _total_field_weight += params.vector[entry.first].fieldWeight;
            }
        }
    }
}


NativeProximityExecutorSharedState::~NativeProximityExecutorSharedState() = default;

void
NativeProximityExecutorSharedState::generateTermPairs(const IQueryEnvironment& env, const QueryTermVector& terms,
                                           uint32_t slidingWindow, FieldSetup& setup)
{
    TermPairVector& pairs = setup.pairs;
    for (size_t i = 0; i < terms.size(); ++i) {
        for (size_t j = i + 1; (j < i + slidingWindow) && (j < terms.size()); ++j) {
            feature_t connectedness = 1;
            for (size_t k = j; k > i; --k) {
                connectedness = std::min(util::lookupConnectedness(env, terms[k].termData()->getUniqueId(),
                                                                   terms[k-1].termData()->getUniqueId(), 0.1),
                                         connectedness);
            }
            connectedness /= (j - i);
            if (terms[i].termData()->getWeight().percent() != 0 ||
                terms[j].termData()->getWeight().percent() != 0)
            { // only consider term pairs with contribution
                pairs.push_back(TermPair(terms[i], terms[j], connectedness));
                setup.divisor += (terms[i].significance() * terms[i].termData()->getWeight().percent() +
                                  terms[j].significance() * terms[j].termData()->getWeight().percent()) * connectedness;
            }
        }
    }
}


feature_t
NativeProximityExecutor::calculateScoreForField(const FieldSetup & fs, uint32_t docId)
{
    feature_t score = 0;
    for (size_t i = 0; i < fs.pairs.size(); ++i) {
        score += calculateScoreForPair(fs.pairs[i], fs.fieldId, docId);
    }
    score *= _params.vector[fs.fieldId].fieldWeight;
    if (fs.divisor > 0) {
        score /= fs.divisor;
    }
    return score;
}

feature_t
NativeProximityExecutor::calculateScoreForPair(const TermPair & pair, uint32_t fieldId, uint32_t docId)
{
    const NativeProximityParam & param = _params.vector[fieldId];
    TermDistanceCalculator::Result result;
    const QueryTerm & a = pair.first;
    const QueryTerm & b = pair.second;
    TermDistanceCalculator::run(a, b, *_md, docId, result);
    uint32_t forwardIdx = result.forwardDist > 0 ? result.forwardDist - 1 : 0;
    uint32_t reverseIdx = result.reverseDist > 0 ? result.reverseDist - 1 : 0;
    feature_t forwardScore = param.proximityTable->get(forwardIdx) * param.proximityImportance;
    feature_t reverseScore = param.revProximityTable->get(reverseIdx) * (1 - param.proximityImportance);
    feature_t termPairWeight = pair.connectedness *
        (a.significance() * a.termData()->getWeight().percent() +
         b.significance() * b.termData()->getWeight().percent());
    feature_t score = (forwardScore + reverseScore) * termPairWeight / param.maxTableSum;
    return score;
}

NativeProximityExecutor::NativeProximityExecutor(const NativeProximityExecutorSharedState& shared_state)
    : FeatureExecutor(),
      _params(shared_state.get_params()),
      _setups(shared_state.get_setups()),
      _totalFieldWeight(shared_state.get_total_field_weight()),
      _md(nullptr)
{
    auto& fields = shared_state.get_fields();
    for (const auto& entry : fields) {
        for (const auto& qt : entry.second) {
            // Record that we need normal term field match data
            (void) qt.termData()->lookupField(entry.first)->getHandle(MatchDataDetails::Normal);
        }
    }
}

void
NativeProximityExecutor::execute(uint32_t docId)
{
    feature_t score = 0;
    for (size_t i = 0; i < _setups.size(); ++i) {
        score += calculateScoreForField(_setups[i], docId);
    }
    if (_totalFieldWeight > 0) {
        score /= _totalFieldWeight;
    }
    outputs().set_number(0, score);
}

void
NativeProximityExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

NativeProximityBlueprint::NativeProximityBlueprint() :
    Blueprint("nativeProximity"),
    _params(),
    _defaultProximityBoost("expdecay(500,3)"),
    _defaultRevProximityBoost("expdecay(400,3)"),
    _shared_state_key()
{
}

NativeProximityBlueprint::~NativeProximityBlueprint() = default;

void
NativeProximityBlueprint::visitDumpFeatures(const IIndexEnvironment & env,
                                            IDumpFeatureVisitor & visitor) const
{
    (void) env;
    visitor.visitDumpFeature(getBaseName());
}

Blueprint::UP
NativeProximityBlueprint::createInstance() const
{
    return std::make_unique<NativeProximityBlueprint>();
}

bool
NativeProximityBlueprint::setup(const IIndexEnvironment & env,
                                const ParameterList & params)
{
    vespalib::asciistream shared_state_key_builder;
    _params.resize(env.getNumFields());
    _params.slidingWindow = util::strToNum<uint32_t>(env.getProperties().lookup(getBaseName(), "slidingWindowSize").get("4"));
    FieldWrapper fields(env, params, FieldType::INDEX);
    vespalib::string defaultProximityImportance  = env.getProperties().lookup(getBaseName(), "proximityImportance").get("0.5");
    shared_state_key_builder << "fef.nativeProximity[";
    bool first_field = true;
    for (uint32_t i = 0; i < fields.getNumFields(); ++i) {
        const FieldInfo * info = fields.getField(i);
        uint32_t fieldId = info->id();
        NativeProximityParam & param = _params.vector[fieldId];
        param.field = true;
        if ((param.proximityTable =
             util::lookupTable(env, getBaseName(), "proximityTable", info->name(), _defaultProximityBoost)) == nullptr)
        {
            return false;
        }
        if ((param.revProximityTable =
             util::lookupTable(env, getBaseName(), "reverseProximityTable", info->name(), _defaultRevProximityBoost)) == nullptr)
        {
            return false;
        }
        param.fieldWeight = indexproperties::FieldWeight::lookup(env.getProperties(), info->name());
        if (param.fieldWeight == 0 ||
            info->isFilter())
        {
            param.field = false;
        }
        param.proximityImportance = util::strToNum<feature_t>
            (env.getProperties().lookup(getBaseName(), "proximityImportance", info->name()).
             get(defaultProximityImportance));

        if (NativeRankBlueprint::useTableNormalization(env)) {
            const Table * fp = param.proximityTable;
            const Table * rp = param.revProximityTable;
            if (fp != nullptr && rp != nullptr) {
                double value = (fp->max() * param.proximityImportance) +
                    (rp->max() * (1 - param.proximityImportance));
                _params.setMaxTableSums(fieldId, value);
            }
        }
        if (param.field) {
            if (first_field) {
                first_field = false;
            } else {
                shared_state_key_builder << ",";
            }
            shared_state_key_builder << info->name();
        }
    }
    shared_state_key_builder << "]";
    _shared_state_key = shared_state_key_builder.str();

    describeOutput("score", "The native proximity score");
    return true;
}

FeatureExecutor &
NativeProximityBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    auto *shared_state = dynamic_cast<const NativeProximityExecutorSharedState *>(env.getObjectStore().get(_shared_state_key));
    if (shared_state == nullptr) {
        shared_state = &stash.create<NativeProximityExecutorSharedState>(env, _params);
    }
    if (shared_state->empty()) {
        return stash.create<SingleZeroValueExecutor>();
    } else {
        return stash.create<NativeProximityExecutor>(*shared_state);
    }

}

void
NativeProximityBlueprint::prepareSharedState(const IQueryEnvironment &queryEnv, IObjectStore &objectStore) const {
    QueryTermHelper::lookupAndStoreQueryTerms(queryEnv, objectStore);
    if (objectStore.get(_shared_state_key) == nullptr) {
        objectStore.add(_shared_state_key, std::make_unique<NativeProximityExecutorSharedState>(queryEnv, _params));
    }
}

}
