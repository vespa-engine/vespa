// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryenvironment.h"
#include "isearchcontext.h"
#include "query.h"
#include "viewresolver.h"
#include "querylimiter.h"
#include "match_phase_limiter.h"
#include "handlerecorder.h"
#include "requestcontext.h"
#include <vespa/searchcommon/attribute/i_attribute_functor.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/searchlib/queryeval/idiversifier.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/clock.h>

namespace search::engine { class Trace; }

namespace search::fef {
    class RankProgram;
    class RankSetup;
}
namespace proton::matching {

class MatchTools
{
private:
    using IRequestContext = search::queryeval::IRequestContext;
    QueryLimiter                          &_queryLimiter;
    const vespalib::Doom                  &_doom;
    const Query                           &_query;
    MaybeMatchPhaseLimiter                &_match_limiter;
    const QueryEnvironment                &_queryEnv;
    const search::fef::RankSetup          &_rankSetup;
    const search::fef::Properties         &_featureOverrides;
    std::unique_ptr<search::fef::MatchData>     _match_data;
    std::unique_ptr<search::fef::RankProgram>   _rank_program;
    search::queryeval::SearchIterator::UP  _search;
    HandleRecorder::HandleMap              _used_handles;
    bool                                   _search_has_changed;
    void setup(std::unique_ptr<search::fef::RankProgram>, double termwise_limit = 1.0);
public:
    typedef std::unique_ptr<MatchTools> UP;
    MatchTools(const MatchTools &) = delete;
    MatchTools & operator = (const MatchTools &) = delete;
    MatchTools(QueryLimiter & queryLimiter,
               const vespalib::Doom & doom,
               const Query &query,
               MaybeMatchPhaseLimiter &match_limiter_in,
               const QueryEnvironment &queryEnv,
               const search::fef::MatchDataLayout &mdl,
               const search::fef::RankSetup &rankSetup,
               const search::fef::Properties &featureOverrides);
    ~MatchTools();
    const vespalib::Doom &getDoom() const { return _doom; }
    QueryLimiter & getQueryLimiter() { return _queryLimiter; }
    MaybeMatchPhaseLimiter &match_limiter() { return _match_limiter; }
    bool has_second_phase_rank() const;
    const search::fef::MatchData &match_data() const { return *_match_data; }
    search::fef::RankProgram &rank_program() { return *_rank_program; }
    search::queryeval::SearchIterator &search() { return *_search; }
    search::queryeval::SearchIterator::UP borrow_search() { return std::move(_search); }
    void give_back_search(search::queryeval::SearchIterator::UP search_in) { _search = std::move(search_in); }
    void tag_search_as_changed() { _search_has_changed = true; }
    void setup_first_phase();
    void setup_second_phase();
    void setup_summary();
    void setup_dump();
};

class AttributeOperationTask {
public:
    using IAttributeFunctor = search::attribute::IAttributeFunctor;
    AttributeOperationTask(const RequestContext & requestContext,
                           vespalib::stringref attribute, vespalib::stringref operation);
    template<typename Hits>
    void run(Hits hits) const;
private:
    search::attribute::BasicType getAttributeType() const;
    const vespalib::string & getOperation() const { return _operation; }
    const RequestContext & _requestContext;
    vespalib::string _attribute;
    vespalib::string _operation;
};

class MatchToolsFactory
{
private:
    using IAttributeFunctor = search::attribute::IAttributeFunctor;
    QueryLimiter                    & _queryLimiter;
    RequestContext                    _requestContext;
    Query                             _query;
    MaybeMatchPhaseLimiter::UP        _match_limiter;
    QueryEnvironment                  _queryEnv;
    search::fef::MatchDataLayout      _mdl;
    const search::fef::RankSetup    & _rankSetup;
    const search::fef::Properties   & _featureOverrides;
    DiversityParams                   _diversityParams;
    bool                              _valid;

    std::unique_ptr<AttributeOperationTask>
    createTask(vespalib::stringref attribute, vespalib::stringref operation) const;
public:
    using UP = std::unique_ptr<MatchToolsFactory>;
    using BasicType = search::attribute::BasicType;

    MatchToolsFactory(QueryLimiter & queryLimiter,
                      const vespalib::Doom & softDoom,
                      ISearchContext &searchContext,
                      search::attribute::IAttributeContext &attributeContext,
                      search::engine::Trace & trace,
                      vespalib::stringref queryStack,
                      const vespalib::string &location,
                      const ViewResolver &viewResolver,
                      const search::IDocumentMetaStore &metaStore,
                      const search::fef::IIndexEnvironment &indexEnv,
                      const search::fef::RankSetup &rankSetup,
                      const search::fef::Properties &rankProperties,
                      const search::fef::Properties &featureOverrides);
    ~MatchToolsFactory();
    bool valid() const { return _valid; }
    const MaybeMatchPhaseLimiter &match_limiter() const { return *_match_limiter; }
    MatchTools::UP createMatchTools() const;
    bool should_diversify() const { return _diversityParams.enabled(); }
    std::unique_ptr<search::queryeval::IDiversifier> createDiversifier(uint32_t heapSize) const;
    search::queryeval::Blueprint::HitEstimate estimate() const { return _query.estimate(); }
    bool has_first_phase_rank() const;
    std::unique_ptr<AttributeOperationTask> createOnMatchTask() const;
    std::unique_ptr<AttributeOperationTask> createOnReRankTask() const;
    std::unique_ptr<AttributeOperationTask> createOnSummaryTask() const;

    const Query & query() const { return _query; }
    const RequestContext & getRequestContext() const { return _requestContext; }
};

}
