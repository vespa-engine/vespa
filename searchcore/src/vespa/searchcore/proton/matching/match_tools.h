// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryenvironment.h"
#include "isearchcontext.h"
#include "query.h"
#include "viewresolver.h"
#include <vespa/vespalib/util/doom.h>
#include "querylimiter.h"
#include "match_phase_limiter.h"
#include "handlerecorder.h"
#include "requestcontext.h"

#include <vespa/vespalib/util/clock.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/common/idocumentmetastore.h>

namespace proton {
namespace matching {

class MatchTools
{
private:
    using IRequestContext = search::queryeval::IRequestContext;
    QueryLimiter                       & _queryLimiter;
    const vespalib::Doom               & _softDoom;
    const vespalib::Doom               & _hardDoom;
    const Query                        & _query;
    MaybeMatchPhaseLimiter             & _match_limiter;
    const QueryEnvironment             & _queryEnv;
    const search::fef::RankSetup       & _rankSetup;
    const search::fef::Properties      & _featureOverrides;
    search::fef::MatchDataLayout         _mdl;
    HandleRecorder                       _handleRecorder;
public:
    typedef std::unique_ptr<MatchTools> UP;
    MatchTools(const MatchTools &) = delete;
    MatchTools & operator = (const MatchTools &) = delete;
    MatchTools(QueryLimiter & queryLimiter,
               const vespalib::Doom & softDoom,
               const vespalib::Doom & hardDoom,
               const Query &query,
               MaybeMatchPhaseLimiter &match_limiter_in,
               const QueryEnvironment &queryEnv,
               const search::fef::MatchDataLayout &mdl,
               const search::fef::RankSetup &rankSetup,
               const search::fef::Properties &featureOverrides);
    const vespalib::Doom &getSoftDoom() const { return _softDoom; }
    const vespalib::Doom &getHardDoom() const { return _hardDoom; }
    QueryLimiter & getQueryLimiter() { return _queryLimiter; }
    MaybeMatchPhaseLimiter &match_limiter() { return _match_limiter; }
    search::queryeval::SearchIterator::UP
    createSearch(search::fef::MatchData &matchData) const {
        return _query.createSearch(matchData);
    }
    bool has_first_phase_rank() const { return !_rankSetup.getFirstPhaseRank().empty(); }
    bool has_second_phase_rank() const { return !_rankSetup.getSecondPhaseRank().empty(); }
    search::fef::RankProgram::UP first_phase_program() const;
    search::fef::RankProgram::UP second_phase_program() const;
    search::fef::RankProgram::UP summary_program() const;
    search::fef::RankProgram::UP dump_program() const;
};

class MatchToolsFactory : public vespalib::noncopyable
{
private:
    QueryLimiter                  & _queryLimiter;
    RequestContext                  _requestContext;
    const vespalib::Doom            _hardDoom;
    Query                           _query;
    MaybeMatchPhaseLimiter::UP      _match_limiter;
    QueryEnvironment                _queryEnv;
    search::fef::MatchDataLayout    _mdl;
    const search::fef::RankSetup  & _rankSetup;
    const search::fef::Properties & _featureOverrides;
    bool                            _valid;
public:
    typedef std::unique_ptr<MatchToolsFactory> UP;

    MatchToolsFactory(QueryLimiter & queryLimiter,
                      const vespalib::Doom & softDoom,
                      const vespalib::Doom & hardDoom,
                      ISearchContext &searchContext,
                      search::attribute::IAttributeContext &attributeContext,
                      const vespalib::stringref &queryStack,
                      const vespalib::string &location,
                      const ViewResolver &viewResolver,
                      const search::IDocumentMetaStore &metaStore,
                      const search::fef::IIndexEnvironment &indexEnv,
                      const search::fef::RankSetup &rankSetup,
                      const search::fef::Properties &rankProperties,
                      const search::fef::Properties &featureOverrides);
    bool valid() const { return _valid; }
    const MaybeMatchPhaseLimiter &match_limiter() const { return *_match_limiter; }
    MatchTools::UP createMatchTools() const;
    search::queryeval::Blueprint::HitEstimate estimate() const { return _query.estimate(); }
};

} // namespace matching
} // namespace proton
