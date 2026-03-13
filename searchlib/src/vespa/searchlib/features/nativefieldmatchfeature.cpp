// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nativefieldmatchfeature.h"
#include "valuefeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

const uint32_t NativeFieldMatchParam::NOT_DEF_FIELD_LENGTH(std::numeric_limits<uint32_t>::max());

NativeFieldMatchExecutorSharedState::NativeFieldMatchExecutorSharedState(const IQueryEnvironment& env,
                                                                         const NativeFieldMatchParams& params)
    : fef::Anything(),
      _params(params),
      _query_terms(),
      _divisor(0)
{
    QueryTermHelper queryTerms(env);
    for (const QueryTerm & qtTmp : queryTerms.terms()) {
        if (qtTmp.termData()->getWeight().percent() != 0) // only consider query terms with contribution
        {
            MyQueryTerm qt(qtTmp);
            using FRA = search::fef::ITermFieldRangeAdapter;
            uint32_t totalFieldWeight = 0;
            for (FRA iter(*qt.termData()); iter.valid(); iter.next()) {
                const ITermFieldData& tfd = iter.get();
                uint32_t fieldId = tfd.getFieldId();
                if (_params.considerField(fieldId)) { // only consider fields with contribution
                    totalFieldWeight += _params.vector[fieldId].fieldWeight;
                    qt.handles().emplace_back(tfd.getHandle(), &tfd);
                }
            }
            if (!qt.handles().empty()) {
                _query_terms.push_back(qt);
                _divisor += (qt.significance() * qt.termData()->getWeight().percent() * totalFieldWeight);
            }
        }
    }
}

NativeFieldMatchExecutorSharedState::~NativeFieldMatchExecutorSharedState() = default;

feature_t
NativeFieldMatchExecutor::calculateScore(const MyQueryTerm &qt, uint32_t docId)
{
    feature_t termScore = 0;
    for (size_t i = 0; i < qt.handles().size(); ++i) {
        TermFieldHandle tfh = qt.handles()[i].first;
        const TermFieldMatchData *tfmd = _md->resolveTermField(tfh);
        const NativeFieldMatchParam & param = _params.vector[tfmd->getFieldId()];
        if (tfmd->has_ranking_data(docId)) { // do we have a hit
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

NativeFieldMatchExecutor::NativeFieldMatchExecutor(const NativeFieldMatchExecutorSharedState& shared_state)
    : FeatureExecutor(),
      _params(shared_state.get_params()),
      _queryTerms(shared_state.get_query_terms()),
      _divisor(shared_state.get_divisor()),
      _md(nullptr)
{
    for (const auto& qt : _queryTerms) {
        for (const auto& handle : qt.handles()) {
            // Record that we need normal term field match data
            (void) handle.second->getHandle(MatchDataDetails::Normal);
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
    _defaultNumOcc("loggrowth(1500,4000,19)"),
    _shared_state_key()
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
    vespalib::asciistream shared_state_key_builder;
    _params.resize(env.getNumFields());
    FieldWrapper fields(env, params, FieldType::INDEX);
    std::string defaultFirstOccImportance = env.getProperties().lookup(getBaseName(), "firstOccurrenceImportance").get("0.5");
    shared_state_key_builder << "fef.nativeFieldMatch[";
    bool first_field = true;
    for (uint32_t i = 0; i < fields.getNumFields(); ++i) {
        const FieldInfo * info = fields.getField(i);
        uint32_t fieldId = info->id();
        NativeFieldMatchParam & param = _params.vector[fieldId];
        param.field = true;
        if ((param.firstOccTable =
             util::lookupTable(env, getBaseName(), "firstOccurrenceTable", info->name(), _defaultFirstOcc)) == nullptr)
        {
            return false;
        }
        if ((param.numOccTable =
             util::lookupTable(env, getBaseName(), "occurrenceCountTable", info->name(), _defaultNumOcc)) == nullptr)
        {
            return false;
        }
        param.fieldWeight = indexproperties::FieldWeight::lookup(env.getProperties(), info->name());
        if (param.fieldWeight == 0 ||
            info->isFilter())
        {
            param.field = false;
        }
        std::string alt_name = FeatureNameBuilder()
                .baseName(getBaseName())
                .parameter(info->name())
                .buildName();

        Property afl = env.getProperties().lookup(alt_name, "averageFieldLength");
        if (!afl.found()) {
            afl = env.getProperties().lookup(getBaseName(), "averageFieldLength", info->name());
        }
        if (afl.found()) {
            param.averageFieldLength = util::strToNum<uint32_t>(afl.get());
        }

        std::string alt_importance = env.getProperties()
                .lookup(alt_name, "firstOccurrenceImportance")
                .get(defaultFirstOccImportance);
        std::string importance = env.getProperties()
                .lookup(getBaseName(), "firstOccurrenceImportance", info->name())
                .get(alt_importance);
        param.firstOccImportance = util::strToNum<feature_t>(importance);

        if (NativeRankBlueprint::useTableNormalization(env)) {
            const Table * fo = param.firstOccTable;
            const Table * no = param.numOccTable;
            if (fo != nullptr && no != nullptr) {
                double value = (fo->max() * param.firstOccImportance) +
                    (no->max() * (1 - param.firstOccImportance));
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
    _shared_state_key = shared_state_key_builder.view();
    _params.minFieldLength = util::strToNum<uint32_t>(env.getProperties().lookup
                                                      (getBaseName(), "minFieldLength").get("6"));

    describeOutput("score", "The native field match score");
    return true;
}

FeatureExecutor &
NativeFieldMatchBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    auto *shared_state = dynamic_cast<const NativeFieldMatchExecutorSharedState *>(env.getObjectStore().get(_shared_state_key));
    if (shared_state == nullptr) {
        shared_state = &stash.create<NativeFieldMatchExecutorSharedState>(env, _params);
    }
    if (shared_state->empty()) {
        return stash.create<SingleZeroValueExecutor>();
    } else {
        return stash.create<NativeFieldMatchExecutor>(*shared_state);
    }
}

void
NativeFieldMatchBlueprint::prepareSharedState(const IQueryEnvironment &queryEnv, IObjectStore &objectStore) const {
    QueryTermHelper::lookupAndStoreQueryTerms(queryEnv, objectStore);
    if (objectStore.get(_shared_state_key) == nullptr) {
        objectStore.add(_shared_state_key, std::make_unique<NativeFieldMatchExecutorSharedState>(queryEnv, _params));
    }
}

}
