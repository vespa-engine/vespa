// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_tools.h"
#include "querynodes.h"
#include "rangequerylocator.h"
#include <vespa/searchcorespi/index/indexsearchable.h>
#include <vespa/searchlib/attribute/attribute_operation.h>
#include <vespa/searchlib/attribute/diversity.h>
#include <vespa/searchlib/engine/trace.h>
#include <vespa/searchlib/features/first_phase_rank_lookup.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/searchlib/queryeval/create_blueprint_params.h>
#include <vespa/searchlib/queryeval/flow.h>
#include <vespa/searchlib/queryeval/wand/wand_parts.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/thread_bundle.h>

using search::attribute::BasicType;
using search::attribute::diversity::DiversityFilter;
using search::queryeval::CreateBlueprintParams;
using search::queryeval::ExecuteInfo;
using search::queryeval::IDiversifier;
using vespalib::Issue;

using namespace search::fef::indexproperties::matchphase;
using namespace search::fef::indexproperties::matching;
using namespace search::fef::indexproperties;
using search::IDocumentMetaStore;
using search::StringStringMap;

namespace proton::matching {

namespace {

using search::fef::Properties;
using search::fef::RankSetup;
using search::fef::IIndexEnvironment;
using search::queryeval::InFlow;
using search::queryeval::wand::StopWordStrategy;

using namespace vespalib::literals;

bool contains_all(const HandleRecorder::HandleMap &old_map,
                  const HandleRecorder::HandleMap &new_map)
{
    for (const auto &handle: new_map) {
        const auto old_itr = old_map.find(handle.first);
        if (old_itr == old_map.end() ||
            ((int(handle.second) & ~int(old_itr->second)) != 0)) {
            return false;
        }
    }
    return true;
}

DegradationParams
extractDegradationParams(const RankSetup &rankSetup, const std::string & attribute, const Properties &rankProperties)
{
    return { attribute,
             DegradationMaxHits::lookup(rankProperties, rankSetup.getDegradationMaxHits()),
             !DegradationAscendingOrder::lookup(rankProperties, rankSetup.isDegradationOrderAscending()),
             DegradationMaxFilterCoverage::lookup(rankProperties, rankSetup.getDegradationMaxFilterCoverage()),
             DegradationSamplePercentage::lookup(rankProperties, rankSetup.getDegradationSamplePercentage()),
             DegradationPostFilterMultiplier::lookup(rankProperties, rankSetup.getDegradationPostFilterMultiplier())};

}

DiversityParams
extractDiversityParams(const RankSetup &rankSetup, const Properties &rankProperties)
{
    return { DiversityAttribute::lookup(rankProperties, rankSetup.getDiversityAttribute()),
             DiversityMinGroups::lookup(rankProperties, rankSetup.getDiversityMinGroups()),
             DiversityCutoffFactor::lookup(rankProperties, rankSetup.getDiversityCutoffFactor()),
             AttributeLimiter::toDiversityCutoffStrategy(DiversityCutoffStrategy::lookup(rankProperties, rankSetup.getDiversityCutoffStrategy())) };
}

} // namespace proton::matching::<unnamed>

void
MatchTools::setup(std::unique_ptr<RankProgram> rank_program, ExecutionProfiler *profiler, double termwise_limit)
{
    if (_search) {
        _match_data->soft_reset();
    }
    _rank_program = std::move(rank_program);
    HandleRecorder recorder;
    {
        HandleRecorder::Binder bind(recorder);
        _rank_program->setup(*_match_data, _queryEnv, _featureOverrides, profiler);
    }
    bool can_reuse_search = (allow_reuse_search() &&
                             _search && !_search_has_changed &&
                             contains_all(_used_handles, recorder.get_handles()));
    if (!can_reuse_search) {
        recorder.tag_match_data(*_match_data);
        _match_data->set_termwise_limit(termwise_limit);
        _search = _query.createSearch(*_match_data);
        _used_handles = std::move(recorder).steal_handles();
        _search_has_changed = false;
    }
}

MatchTools::MatchTools(QueryLimiter & queryLimiter,
                       const vespalib::Doom & doom,
                       const Query &query,
                       MaybeMatchPhaseLimiter & match_limiter_in,
                       const QueryEnvironment & queryEnv,
                       const MatchDataLayout & mdl,
                       const RankSetup & rankSetup,
                       const Properties & featureOverrides)
    : _queryLimiter(queryLimiter),
      _doom(doom),
      _query(query),
      _match_limiter(match_limiter_in),
      _queryEnv(queryEnv),
      _rankSetup(rankSetup),
      _featureOverrides(featureOverrides),
      _match_data(mdl.createMatchData()),
      _rank_program(),
      _search(),
      _used_handles(),
      _search_has_changed(false)
{
}

MatchTools::~MatchTools() = default;

bool
MatchTools::has_second_phase_rank() const {
    return !_rankSetup.getSecondPhaseRank().empty();
}

void
MatchTools::setup_first_phase(ExecutionProfiler *profiler)
{
    setup(_rankSetup.create_first_phase_program(), profiler,
          TermwiseLimit::lookup(_queryEnv.getProperties(), _rankSetup.get_termwise_limit()));
}

void
MatchTools::setup_second_phase(ExecutionProfiler *profiler)
{
    setup(_rankSetup.create_second_phase_program(), profiler);
}

void
MatchTools::setup_match_features()
{
    setup(_rankSetup.create_match_program(), nullptr);
}

void
MatchTools::setup_summary()
{
    setup(_rankSetup.create_summary_program(), nullptr);
}

void
MatchTools::setup_dump()
{
    setup(_rankSetup.create_dump_program(), nullptr);
}

//-----------------------------------------------------------------------------

MatchToolsFactory::
MatchToolsFactory(QueryLimiter               & queryLimiter,
                  const vespalib::Doom       & doom,
                  ISearchContext             & searchContext,
                  IAttributeContext          & attributeContext,
                  search::engine::Trace      & root_trace,
                  std::string_view             queryStack,
                  const std::string          & location,
                  const ViewResolver         & viewResolver,
                  const IDocumentMetaStore   & metaStore,
                  const IIndexEnvironment    & indexEnv,
                  const RankSetup            & rankSetup,
                  const Properties           & rankProperties,
                  const Properties           & featureOverrides,
                  vespalib::ThreadBundle     & thread_bundle,
                  const search::IDocumentMetaStoreContext::IReadGuard::SP * metaStoreReadGuard,
                  uint32_t                     maxNumHits,
                  bool                         is_search)
    : _queryLimiter(queryLimiter),
      _create_blueprint_params(extract_create_blueprint_params(rankSetup, rankProperties, metaStore.getNumActiveLids(), searchContext.getDocIdLimit())),
      _query(),
      _match_limiter(),
      _queryEnv(indexEnv, attributeContext, rankProperties, searchContext.getIndexes()),
      _requestContext(doom, thread_bundle, attributeContext, _queryEnv, _queryEnv.getObjectStore(),
                      _create_blueprint_params, metaStoreReadGuard),
      _mdl(),
      _rankSetup(rankSetup),
      _featureOverrides(featureOverrides),
      _diversityParams(),
      _valid(false),
      _first_phase_rank_lookup(nullptr),
      _metaStore(metaStore)
{
    if (doom.soft_doom()) return;
    auto trace = root_trace.make_trace();
    trace.addEvent(4, "Start query setup");
    _query.setWhiteListBlueprint(metaStore.createWhiteListBlueprint());
    trace.addEvent(5, "Deserialize and build query tree");
    _valid = _query.buildTree(queryStack, location, viewResolver, indexEnv,
                              AlwaysMarkPhraseExpensive::check(_queryEnv.getProperties(), rankSetup.always_mark_phrase_expensive()));
    if (_valid) {
        _query.extractTerms(_queryEnv.terms());
        _query.extractLocations(_queryEnv.locations());
        trace.addEvent(5, "Build query execution plan");
        _query.reserveHandles(_requestContext, searchContext, _mdl);
        if (trace.getLevel() >= 6) { // will dump blueprint later
            _query.enumerate_blueprint_nodes();
        }
        trace.addEvent(5, "Optimize query execution plan");
        bool sort_by_cost = SortBlueprintsByCost::check(_queryEnv.getProperties(), rankSetup.sort_blueprints_by_cost());
        double hitRate = std::min(1.0, double(maxNumHits)/double(searchContext.getDocIdLimit()));
        auto in_flow = InFlow(is_search, hitRate);
        _query.optimize(in_flow, sort_by_cost);
        trace.addEvent(4, "Perform dictionary lookups and posting lists initialization");
        _query.fetchPostings(ExecuteInfo::create(in_flow.rate(), _requestContext.getDoom(), thread_bundle));
        if (is_search) {
            _query.handle_global_filter(_requestContext, searchContext.getDocIdLimit(),
                                        _create_blueprint_params.global_filter_lower_limit,
                                        _create_blueprint_params.global_filter_upper_limit, trace, sort_by_cost);
        }
        _query.freeze();
        trace.addEvent(5, "Prepare shared state for multi-threaded rank executors");
        _rankSetup.prepareSharedState(_queryEnv, _queryEnv.getObjectStore());
        _first_phase_rank_lookup = FirstPhaseRankLookup::get_mutable_shared_state(_queryEnv.getObjectStore());
        _diversityParams = extractDiversityParams(_rankSetup, rankProperties);
        std::string attribute = DegradationAttribute::lookup(rankProperties, _rankSetup.getDegradationAttribute());
        DegradationParams degradationParams = extractDegradationParams(_rankSetup, attribute, rankProperties);

        if (degradationParams.enabled()) {
            trace.addEvent(5, "Setup match phase limiter");
            const search::fef::FieldInfo * fieldInfo = indexEnv.getFieldByName(attribute);
            uint32_t field_id = fieldInfo != nullptr ? fieldInfo->id() : 0;
            _rangeLocator = std::make_unique<LocateRangeItemFromQuery>(*_query.peekRoot(), field_id);
            _match_limiter = std::make_unique<MatchPhaseLimiter>(metaStore.getCommittedDocIdLimit(), *_rangeLocator,
                                                                 searchContext.getAttributes(), _requestContext,
                                                                 degradationParams, _diversityParams);
        }
    }
    if ( ! _match_limiter) {
        _match_limiter = std::make_unique<NoMatchPhaseLimiter>();
    }
    trace.addEvent(4, "Complete query setup");
    root_trace.make_inserter("query_setup"_ssv).handle_nested(trace);
}

MatchToolsFactory::~MatchToolsFactory() = default;

MatchTools::UP
MatchToolsFactory::createMatchTools() const
{
    assert(_valid);
    return std::make_unique<MatchTools>(_queryLimiter, _requestContext.getDoom(), _query,
                                        *_match_limiter, _queryEnv, _mdl, _rankSetup, _featureOverrides);
}

std::unique_ptr<IDiversifier>
MatchToolsFactory::createDiversifier(uint32_t want_hits) const
{
    if ( !_diversityParams.enabled() ) {
        return {};
    }
    auto attr = _requestContext.getAttribute(_diversityParams.attribute);
    if ( !attr) {
        Issue::report("Skipping diversity due to no %s attribute.", _diversityParams.attribute.c_str());
        return {};
    }
    size_t max_per_group = std::max(size_t(1), size_t(want_hits / _diversityParams.min_groups));
    return DiversityFilter::create(*attr, want_hits, max_per_group, _diversityParams.min_groups,
                                   _diversityParams.cutoff_strategy == DiversityParams::CutoffStrategy::STRICT);
}

std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createTask(std::string_view attribute, std::string_view operation) const {
    return (!attribute.empty() && ! operation.empty())
           ? std::make_unique<AttributeOperationTask>(_requestContext, attribute, operation)
           : std::unique_ptr<AttributeOperationTask>();
}

std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createOnMatchTask() const {
    const auto & op = _rankSetup.getMutateOnMatch();
    return createTask(op._attribute, op._operation);
}

std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createOnFirstPhaseTask() const {
    const auto & op = _rankSetup.getMutateOnFirstPhase();
    // Note that combining onmatch in query with first-phase is not a bug.
    // It is intentional, as the semantics of onmatch in query are identical to on-first-phase.
    if (_rankSetup.allowMutateQueryOverride()) {
        return createTask(execute::onmatch::Attribute::lookup(_queryEnv.getProperties(), op._attribute),
                          execute::onmatch::Operation::lookup(_queryEnv.getProperties(), op._operation));
    } else {
        return createTask(op._attribute, op._operation);
    }
}

std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createOnSecondPhaseTask() const {
    const auto & op = _rankSetup.getMutateOnSecondPhase();
    if (_rankSetup.allowMutateQueryOverride()) {
        return createTask(execute::onrerank::Attribute::lookup(_queryEnv.getProperties(), op._attribute),
                          execute::onrerank::Operation::lookup(_queryEnv.getProperties(), op._operation));
    } else {
        return createTask(op._attribute, op._operation);
    }
}

std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createOnSummaryTask() const {
    const auto & op = _rankSetup.getMutateOnSummary();
    if (_rankSetup.allowMutateQueryOverride()) {
        return createTask(execute::onsummary::Attribute::lookup(_queryEnv.getProperties(), op._attribute),
                          execute::onsummary::Operation::lookup(_queryEnv.getProperties(), op._operation));
    } else {
        return createTask(op._attribute, op._operation);
    }
}

bool
MatchToolsFactory::hasOnMatchTask() const {
    return _rankSetup.getMutateOnMatch().enabled();
}

bool
MatchToolsFactory::has_first_phase_rank() const {
    return !_rankSetup.getFirstPhaseRank().empty();
}

bool
MatchToolsFactory::has_match_features() const
{
    return _rankSetup.has_match_features();
}

const StringStringMap &
MatchToolsFactory::get_feature_rename_map() const
{
    return _rankSetup.get_feature_rename_map();
}

CreateBlueprintParams
MatchToolsFactory::extract_create_blueprint_params(const RankSetup& rank_setup, const Properties& rank_properties,
                                                   uint32_t active_docids, uint32_t docid_limit)
{
    double lower_limit = GlobalFilterLowerLimit::lookup(rank_properties, rank_setup.get_global_filter_lower_limit());
    double upper_limit = GlobalFilterUpperLimit::lookup(rank_properties, rank_setup.get_global_filter_upper_limit());
    double target_hits_max_adjustment_factor = TargetHitsMaxAdjustmentFactor::lookup(rank_properties, rank_setup.get_target_hits_max_adjustment_factor());
    auto fuzzy_matching_algorithm = FuzzyAlgorithm::lookup(rank_properties, rank_setup.get_fuzzy_matching_algorithm());
    double weakand_stop_word_adjust_limit = WeakAndStopWordAdjustLimit::lookup(rank_properties, rank_setup.get_weakand_stop_word_adjust_limit());
    double weakand_stop_word_drop_limit = WeakAndStopWordDropLimit::lookup(rank_properties, rank_setup.get_weakand_stop_word_drop_limit());
    double disk_index_bitvector_limit = DiskIndexBitvectorLimit::lookup(rank_properties, rank_setup.get_disk_index_bitvector_limit());

    // Note that we count the reserved docid 0 as active.
    // This ensures that when searchable-copies=1, the ratio is 1.0.
    double active_hit_ratio = std::min(active_docids + 1, docid_limit) / static_cast<double>(docid_limit);

    return {lower_limit * active_hit_ratio,
            upper_limit * active_hit_ratio,
            target_hits_max_adjustment_factor,
            fuzzy_matching_algorithm,
            StopWordStrategy(weakand_stop_word_adjust_limit,
                                                      weakand_stop_word_drop_limit, docid_limit),
            disk_index_bitvector_limit};
}

AttributeOperationTask::AttributeOperationTask(const RequestContext & requestContext,
                                               std::string_view attribute, std::string_view operation)
    : _requestContext(requestContext),
      _attribute(attribute),
      _operation(operation)
{
}

search::attribute::BasicType
AttributeOperationTask::getAttributeType() const {
    auto attr = _requestContext.getAttribute(_attribute);
    return attr ? attr->getBasicType() : BasicType::NONE;
}

using search::attribute::AttributeOperation;

template <typename Hits>
void
AttributeOperationTask::run(Hits docs) const {
    _requestContext.asyncForAttribute(_attribute, AttributeOperation::create(getAttributeType(), getOperation(), std::move(docs)));
}

template void AttributeOperationTask::run(std::vector<AttributeOperation::Hit>) const;
template void AttributeOperationTask::run(std::vector<uint32_t >) const;
template void AttributeOperationTask::run(AttributeOperation::FullResult) const;

}
