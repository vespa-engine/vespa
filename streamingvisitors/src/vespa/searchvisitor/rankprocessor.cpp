// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querytermdata.h"
#include "rankprocessor.h"
#include <vespa/searchlib/fef/handle.h>
#include <vespa/searchlib/fef/simpletermfielddata.h>
#include <vespa/searchlib/query/streaming/equiv_query_node.h>
#include <vespa/searchlib/query/streaming/nearest_neighbor_query_node.h>
#include <vespa/vsm/vsm/fieldsearchspec.h>
#include <algorithm>
#include <cmath>
#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.rankprocessor");

using vespalib::FeatureSet;
using vespalib::FeatureValues;
using search::fef::FeatureHandle;
using search::fef::ITermData;
using search::fef::ITermFieldData;
using search::fef::IllegalHandle;
using search::fef::MatchData;
using search::fef::Properties;
using search::fef::RankProgram;
using search::fef::RankSetup;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;
using search::streaming::Hit;
using search::streaming::HitList;
using search::streaming::MultiTerm;
using search::streaming::Query;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;
using vdslib::SearchResult;

namespace streaming {

namespace {

vespalib::string
getIndexName(const vespalib::string & indexName, const vespalib::string & expandedIndexName)
{
    if (indexName == expandedIndexName) {
        return indexName;
    }
    return indexName + "(" + expandedIndexName + ")";
}

search::fef::LazyValue
getFeature(const RankProgram &rankProgram) {
    search::fef::FeatureResolver resolver(rankProgram.get_seeds());
    assert(resolver.num_features() == 1u);
    return resolver.resolve(0);
}

}

void
RankProcessor::resolve_fields_from_children(QueryTermData& qtd, MultiTerm& mt)
{
    vespalib::hash_set<uint32_t> field_ids;
    for (auto& subterm : mt.get_terms()) {
        vespalib::string expandedIndexName = vsm::FieldSearchSpecMap::stripNonFields(subterm->index());
        const RankManager::View *view = _rankManagerSnapshot->getView(expandedIndexName);
        if (view != nullptr) {
            for (auto field_id : *view) {
                field_ids.insert(field_id);
            }
        } else {
            LOG(warning, "Could not find a view for index '%s'. Ranking no fields.",
                getIndexName(subterm->index(), expandedIndexName).c_str());
        }
    }
    std::vector<uint32_t> sorted_field_ids;
    sorted_field_ids.reserve(field_ids.size());
    for (auto field_id : field_ids) {
        sorted_field_ids.emplace_back(field_id);
    }
    std::sort(sorted_field_ids.begin(), sorted_field_ids.end());
    for (auto field_id : sorted_field_ids) {
        qtd.getTermData().addField(field_id).setHandle(_mdLayout.allocTermField(field_id));
    }
}

void
RankProcessor::resolve_fields_from_term(QueryTermData& qtd, search::streaming::QueryTerm& term)
{
    vespalib::string expandedIndexName = vsm::FieldSearchSpecMap::stripNonFields(term.index());
    const RankManager::View *view = _rankManagerSnapshot->getView(expandedIndexName);
    if (view != nullptr) {
        for (auto field_id : *view) {
            qtd.getTermData().addField(field_id).setHandle(_mdLayout.allocTermField(field_id));
        }
    } else {
        LOG(warning, "Could not find a view for index '%s'. Ranking no fields.",
            getIndexName(term.index(), expandedIndexName).c_str());
    }
    LOG(debug, "Setup query term '%s:%s'",
        getIndexName(term.index(), expandedIndexName).c_str(),
        term.getTerm());
}

void
RankProcessor::initQueryEnvironment()
{
    QueryWrapper::TermList & terms = _query.getTermList();

    for (auto& term : terms) {
        if (term->isGeoLoc()) {
            const vespalib::string & fieldName = term->index();
            const vespalib::string & locStr = term->getTermString();
            _queryEnv.addGeoLocation(fieldName, locStr);
        }
        QueryTermData & qtd = dynamic_cast<QueryTermData &>(term->getQueryItem());

        qtd.getTermData().setWeight(term->weight());
        qtd.getTermData().setUniqueId(term->uniqueId());
        qtd.getTermData().setPhraseLength(term->width());
        auto* nn_term = term->as_nearest_neighbor_query_node();
        if (nn_term != nullptr) {
            qtd.getTermData().set_query_tensor_name(nn_term->get_query_tensor_name());
        }
        auto* eqn = term->as_equiv_query_node();
        if (eqn != nullptr) {
            resolve_fields_from_children(qtd, *eqn);
        } else {
            resolve_fields_from_term(qtd, *term);
        }
        _queryEnv.addTerm(&qtd.getTermData());
    }
    _rankSetup.prepareSharedState(_queryEnv, _queryEnv.getObjectStore());
    _match_data = _mdLayout.createMatchData();
}

void
RankProcessor::initHitCollector(size_t wantedHitCount, bool use_sort_blob)
{
    _hitCollector = std::make_unique<HitCollector>(wantedHitCount, use_sort_blob);
}

void
RankProcessor::setupRankProgram(RankProgram &program)
{
    program.setup(*_match_data, _queryEnv, _featureOverrides);
}

void
RankProcessor::init(bool forRanking, size_t wantedHitCount, bool use_sort_blob)
{
    initQueryEnvironment();
    if (forRanking) {
        if (_rankSetup.getSecondPhaseRank().empty()) {
            _rankProgram = _rankSetup.create_first_phase_program();
        } else {
            // We calculate 2. phase ranking for all hits (no need calculating 1. phase ranking as well)
            _rankProgram = _rankSetup.create_second_phase_program();
        }
        setupRankProgram(*_rankProgram);
        _rankScore = getFeature(*_rankProgram);
        _summaryProgram = _rankSetup.create_summary_program();
        setupRankProgram(*_summaryProgram);
        if (_rankSetup.has_match_features()) {
            _match_features_program = _rankSetup.create_match_program();
            setupRankProgram(*_match_features_program);
        }
    } else {
        _rankProgram = _rankSetup.create_dump_program();
        setupRankProgram(*_rankProgram);
    }
    initHitCollector(wantedHitCount, use_sort_blob);
}

RankProcessor::RankProcessor(std::shared_ptr<const RankManager::Snapshot> snapshot,
                             const vespalib::string &rankProfile,
                             Query & query,
                             const vespalib::string & location,
                             const Properties & queryProperties,
                             const Properties & featureOverrides,
                             const search::IAttributeManager * attrMgr) :

    _rankManagerSnapshot(std::move(snapshot)),
    _rankSetup(_rankManagerSnapshot->getRankSetup(rankProfile)),
    _query(query),
    _queryEnv(location, _rankManagerSnapshot->getIndexEnvironment(rankProfile), queryProperties, attrMgr),
    _featureOverrides(featureOverrides),
    _mdLayout(),
    _match_data(),
    _rankProgram(),
    _docId(TermFieldMatchData::invalidId()),
    _score(0.0),
    _summaryProgram(),
    _zeroScore(),
    _rankScore(&_zeroScore),
    _hitCollector(),
    _match_features_program()
{
}

void
RankProcessor::initForRanking(size_t wantedHitCount, bool use_sort_blob)
{
    return init(true, wantedHitCount, use_sort_blob);
}

void
RankProcessor::initForDumping(size_t wantedHitCount, bool use_sort_blob)
{
    return init(false, wantedHitCount, use_sort_blob);
}

void
RankProcessor::runRankProgram(uint32_t docId)
{
    _score = _rankScore.as_number(docId);
    if (std::isnan(_score) || std::isinf(_score)) {
        _score = -HUGE_VAL;
    }
}

namespace {

void
copyTermFieldMatchData(const std::vector<search::fef::TermFieldMatchData> &src, MatchData &dst)
{
    assert(src.size() == dst.getNumTermFields());
    for (search::fef::TermFieldHandle handle = 0; handle < dst.getNumTermFields(); ++handle) {
        (*dst.resolveTermField(handle)) = src[handle];
    }
}

class RankProgramWrapper : public HitCollector::IRankProgram
{
private:
    MatchData &_match_data;
public:
    explicit RankProgramWrapper(MatchData &match_data) : _match_data(match_data) {}
    void run(uint32_t docid, const std::vector<search::fef::TermFieldMatchData> &matchData) override {
        // Prepare the match data object used by the rank program with earlier unpacked match data.
        copyTermFieldMatchData(matchData, _match_data);
        (void) docid;
    }
};

}

FeatureSet::SP
RankProcessor::calculateFeatureSet()
{
    LOG(debug, "Calculate feature set");
    RankProgram &rankProgram = *(_summaryProgram ? _summaryProgram : _rankProgram);
    search::fef::FeatureResolver resolver(rankProgram.get_seeds(false));
    LOG(debug, "Feature handles: numNames(%ld)", resolver.num_features());
    RankProgramWrapper wrapper(*_match_data);
    FeatureSet::SP sf = _hitCollector->getFeatureSet(wrapper, resolver, _rankSetup.get_feature_rename_map());
    LOG(debug, "Feature set: numFeatures(%u), numDocs(%u)", sf->numFeatures(), sf->numDocs());
    return sf;
}

FeatureSet::SP
RankProcessor::calculateFeatureSet(search::DocumentIdT docId)
{
    LOG(debug, "Calculate feature set for docId = %d", docId);
    RankProgram &rankProgram = *(_summaryProgram ? _summaryProgram : _rankProgram);
    search::fef::FeatureResolver resolver(rankProgram.get_seeds(false));
    LOG(debug, "Feature handles: numNames(%ld)", resolver.num_features());
    RankProgramWrapper wrapper(*_match_data);
    FeatureSet::SP sf = _hitCollector->getFeatureSet(wrapper, docId, resolver, _rankSetup.get_feature_rename_map());
    LOG(debug, "Feature set: numFeatures(%u), numDocs(%u)", sf->numFeatures(), sf->numDocs());
    return sf;
}

FeatureValues
RankProcessor::calculate_match_features()
{
    if (!_match_features_program) {
        return {};
    }
    RankProgramWrapper wrapper(*_match_data);
    search::fef::FeatureResolver resolver(_match_features_program->get_seeds(false));
    return _hitCollector->get_match_features(wrapper, resolver, _rankSetup.get_feature_rename_map());
}

void
RankProcessor::fillSearchResult(vdslib::SearchResult & searchResult)
{
    _hitCollector->fillSearchResult(searchResult, calculate_match_features());
}

void
RankProcessor::unpackMatchData(uint32_t docId)
{
    _docId = docId;
    unpack_match_data(docId, *_match_data, _query);
}

void
RankProcessor::unpack_match_data(uint32_t docid, MatchData &matchData, QueryWrapper& query)
{
    for (auto& term : query.getTermList()) {
        auto & qtd = static_cast<QueryTermData &>(term->getQueryItem());
        const ITermData &td = qtd.getTermData();
        term->unpack_match_data(docid, td, matchData);
    }
}

} // namespace streaming

