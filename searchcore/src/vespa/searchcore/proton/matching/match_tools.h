// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/searchlib/common/stringmap.h>
#include <vespa/searchlib/queryeval/idiversifier.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/clock.h>

namespace vespalib { class ExecutionProfiler; }
namespace vespalib { struct ThreadBundle; }

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
    using SearchIterator = search::queryeval::SearchIterator;
    using MatchData = search::fef::MatchData;
    using MatchDataLayout = search::fef::MatchDataLayout;
    using Properties = search::fef::Properties;
    using RankProgram = search::fef::RankProgram;
    using RankSetup = search::fef::RankSetup;
    using ExecutionProfiler = vespalib::ExecutionProfiler;
    QueryLimiter                    &_queryLimiter;
    const vespalib::Doom            &_doom;
    const Query                     &_query;
    MaybeMatchPhaseLimiter          &_match_limiter;
    const QueryEnvironment          &_queryEnv;
    const RankSetup                 &_rankSetup;
    const Properties                &_featureOverrides;
    std::unique_ptr<MatchData>       _match_data;
    std::unique_ptr<RankProgram>     _rank_program;
    std::unique_ptr<SearchIterator>  _search;
    HandleRecorder::HandleMap        _used_handles;
    bool                             _search_has_changed;
    void setup(std::unique_ptr<RankProgram>, ExecutionProfiler *profiler, double termwise_limit = 1.0);
public:
    using UP = std::unique_ptr<MatchTools>;
    MatchTools(const MatchTools &) = delete;
    MatchTools & operator = (const MatchTools &) = delete;
    MatchTools(QueryLimiter & queryLimiter,
               const vespalib::Doom & doom,
               const Query &query,
               MaybeMatchPhaseLimiter &match_limiter_in,
               const QueryEnvironment &queryEnv,
               const MatchDataLayout &mdl,
               const RankSetup &rankSetup,
               const Properties &featureOverrides);
    ~MatchTools();
    const vespalib::Doom &getDoom() const { return _doom; }
    QueryLimiter & getQueryLimiter() { return _queryLimiter; }
    MaybeMatchPhaseLimiter &match_limiter() { return _match_limiter; }
    bool has_second_phase_rank() const;
    const MatchData &match_data() const { return *_match_data; }
    RankProgram &rank_program() { return *_rank_program; }
    SearchIterator &search() { return *_search; }
    std::unique_ptr<SearchIterator> borrow_search() { return std::move(_search); }
    void give_back_search(std::unique_ptr<SearchIterator> search_in) { _search = std::move(search_in); }
    void tag_search_as_changed() { _search_has_changed = true; }
    void setup_first_phase(ExecutionProfiler *profiler);
    void setup_second_phase(ExecutionProfiler *profiler);
    void setup_match_features();
    void setup_summary();
    void setup_dump();

    // explicitly disallow re-using the search iterator tree (for now)
    //
    // Iterators with internal state that limits the number of hits
    // produced may not match a document during second phase ranking
    // that was matched during first phase ranking. Note that the
    // inverse may also happen; matching a document during second
    // phase matching that was not matched during first phase ranking.
    constexpr bool allow_reuse_search() const noexcept { return false; }
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
    using IAttributeContext = search::attribute::IAttributeContext;
    using AttributeBlueprintParams = search::attribute::AttributeBlueprintParams;
    using MatchDataLayout = search::fef::MatchDataLayout;
    using Properties = search::fef::Properties;
    using RankProgram = search::fef::RankProgram;
    using RankSetup = search::fef::RankSetup;
    using IIndexEnvironment = search::fef::IIndexEnvironment;
    using IDiversifier = search::queryeval::IDiversifier;
    QueryLimiter              & _queryLimiter;
    AttributeBlueprintParams    _global_filter_params;
    Query                       _query;
    MaybeMatchPhaseLimiter::UP  _match_limiter;
    std::unique_ptr<RangeQueryLocator> _rangeLocator;
    QueryEnvironment            _queryEnv;
    RequestContext              _requestContext;
    MatchDataLayout             _mdl;
    const RankSetup           & _rankSetup;
    const Properties          & _featureOverrides;
    DiversityParams             _diversityParams;
    bool                        _valid;

    std::unique_ptr<AttributeOperationTask>
    createTask(vespalib::stringref attribute, vespalib::stringref operation) const;
public:
    using UP = std::unique_ptr<MatchToolsFactory>;
    using BasicType = search::attribute::BasicType;
    using StringStringMap = search::StringStringMap;

    MatchToolsFactory(QueryLimiter & queryLimiter,
                      const vespalib::Doom & softDoom,
                      ISearchContext &searchContext,
                      IAttributeContext &attributeContext,
                      search::engine::Trace & root_trace,
                      vespalib::stringref queryStack,
                      const vespalib::string &location,
                      const ViewResolver &viewResolver,
                      const search::IDocumentMetaStore &metaStore,
                      const IIndexEnvironment &indexEnv,
                      const RankSetup &rankSetup,
                      const Properties &rankProperties,
                      const Properties &featureOverrides,
                      vespalib::ThreadBundle &thread_bundle,
                      bool is_search);
    ~MatchToolsFactory();
    bool valid() const { return _valid; }
    const MaybeMatchPhaseLimiter &match_limiter() const { return *_match_limiter; }
    MatchTools::UP createMatchTools() const;
    bool should_diversify() const { return _diversityParams.enabled(); }
    std::unique_ptr<IDiversifier> createDiversifier(uint32_t heapSize) const;
    search::queryeval::Blueprint::HitEstimate estimate() const { return _query.estimate(); }
    bool has_first_phase_rank() const;
    bool has_match_features() const;
    bool hasOnMatchTask() const;
    std::unique_ptr<AttributeOperationTask> createOnMatchTask() const;
    std::unique_ptr<AttributeOperationTask> createOnFirstPhaseTask() const;
    std::unique_ptr<AttributeOperationTask> createOnSecondPhaseTask() const;
    std::unique_ptr<AttributeOperationTask> createOnSummaryTask() const;

    const Query & query() const { return _query; }
    const RequestContext & getRequestContext() const { return _requestContext; }

    const StringStringMap & get_feature_rename_map() const;

    /**
     * Extracts global filter parameters from the rank-profile and query.
     *
     * These parameters are expected to be in the range [0.0, 1.0], which matches the range of the estimated hit ratio of the query.
     * When searchable-copies > 1, we must scale the parameters to match the effective range of the estimated hit ratio.
     * This is done by multiplying with the active hit ratio (active docids / docid limit).
     */
    static AttributeBlueprintParams
    extract_global_filter_params(const RankSetup& rank_setup, const Properties& rank_properties,
                                 uint32_t active_docids, uint32_t docid_limit);
};

}
