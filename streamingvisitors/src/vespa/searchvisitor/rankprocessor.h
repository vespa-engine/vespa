// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

namespace storage {

/**
 * This class is associated with a query and a rank profile and
 * is used to calculate rank and feature set for matched documents.
 **/
class RankProcessor
{
private:
    RankManager::Snapshot::SP      _rankManagerSnapshot;
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

    void initQueryEnvironment();
    void initHitCollector(size_t wantedHitCount);
    void setupRankProgram(search::fef::RankProgram &program);

    /**
     * Initializes this rank processor.
     * @param forRanking whether this should be used for ranking or dumping.
     * @param wantedHitCount the number of hits we want to return from the hit collector.
     * @return whether the rank processor was initialized or not.
     **/
    void init(bool forRanking, size_t wantedHitCount);

    void unpackMatchData(search::fef::MatchData &matchData);

public:
    typedef std::unique_ptr<RankProcessor> UP;

    RankProcessor(RankManager::Snapshot::SP snapshot,
                  const vespalib::string &rankProfile,
                  search::Query & query,
                  const vespalib::string & location,
                  search::fef::Properties & queryProperties,
                  const search::IAttributeManager * attrMgr);

    void initForRanking(size_t wantedHitCount);
    void initForDumping(size_t wantedHitCount);
    void unpackMatchData(uint32_t docId);
    void runRankProgram(uint32_t docId);
    search::FeatureSet::SP calculateFeatureSet();
    void fillSearchResult(vdslib::SearchResult & searchResult);
    const search::fef::MatchData &getMatchData() const { return *_match_data; }
    void setRankScore(double score) { _score = score; } 
    double getRankScore() const { return _score; }
    HitCollector & getHitCollector() { return *_hitCollector; }
    uint32_t getDocId() const { return _docId; }
};

} // namespace storage

