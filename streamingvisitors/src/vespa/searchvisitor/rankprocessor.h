// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/vdslib/container/searchresult.h>
#include "hitcollector.h"
#include "queryenvironment.h"
#include "querywrapper.h"
#include "rankmanager.h"

namespace streaming {

/**
 * This class is associated with a query and a rank profile and
 * is used to calculate rank and feature set for matched documents.
 **/
class RankProcessor
{
private:
    using RankProgram = search::fef::RankProgram;
    using FeatureSet = vespalib::FeatureSet;
    using FeatureValues = vespalib::FeatureValues;
    std::shared_ptr<const RankManager::Snapshot> _rankManagerSnapshot;
    const search::fef::RankSetup & _rankSetup;
    QueryWrapper                   _query;

    QueryEnvironment                     _queryEnv;
    search::fef::MatchDataLayout         _mdLayout;
    search::fef::MatchData::UP           _match_data;
    search::fef::RankProgram::UP         _rankProgram;
    uint32_t                             _docId;
    double                               _score;
    search::fef::RankProgram::UP         _summaryProgram;
    search::fef::NumberOrObject          _zeroScore;
    search::fef::LazyValue               _rankScore;
    HitCollector::UP                     _hitCollector;
    std::unique_ptr<RankProgram>         _match_features_program;

    void initQueryEnvironment();
    void initHitCollector(size_t wantedHitCount);
    void setupRankProgram(search::fef::RankProgram &program);
    FeatureValues calculate_match_features();

    /**
     * Initializes this rank processor.
     * @param forRanking whether this should be used for ranking or dumping.
     * @param wantedHitCount the number of hits we want to return from the hit collector.
     * @return whether the rank processor was initialized or not.
     **/
    void init(bool forRanking, size_t wantedHitCount);

public:
    using UP = std::unique_ptr<RankProcessor>;

    RankProcessor(std::shared_ptr<const RankManager::Snapshot> snapshot,
                  const vespalib::string &rankProfile,
                  search::streaming::Query & query,
                  const vespalib::string & location,
                  search::fef::Properties & queryProperties,
                  const search::IAttributeManager * attrMgr);

    void initForRanking(size_t wantedHitCount);
    void initForDumping(size_t wantedHitCount);
    void unpackMatchData(uint32_t docId);
    static void unpack_match_data(uint32_t docid, search::fef::MatchData& matchData, QueryWrapper& query);
    void runRankProgram(uint32_t docId);
    vespalib::FeatureSet::SP calculateFeatureSet();
    void fillSearchResult(vdslib::SearchResult & searchResult);
    const search::fef::MatchData &getMatchData() const { return *_match_data; }
    void setRankScore(double score) { _score = score; } 
    double getRankScore() const { return _score; }
    HitCollector & getHitCollector() { return *_hitCollector; }
    uint32_t getDocId() const { return _docId; }
    search::fef::IQueryEnvironment& get_query_env() { return _queryEnv; }
    QueryEnvironment& get_real_query_env() { return _queryEnv; }
};

} // namespace streaming

