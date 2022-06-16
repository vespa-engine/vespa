// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_tools.h"
#include "querynodes.h"
#include <vespa/searchcorespi/index/indexsearchable.h>
#include <vespa/searchlib/attribute/attribute_blueprint_params.h>
#include <vespa/searchlib/attribute/attribute_operation.h>
#include <vespa/searchlib/attribute/diversity.h>
#include <vespa/searchlib/engine/trace.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inject.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/util/issue.h>

using search::attribute::IAttributeContext;
using search::queryeval::IRequestContext;
using search::queryeval::IDiversifier;
using search::attribute::diversity::DiversityFilter;
using search::attribute::BasicType;
using search::attribute::AttributeBlueprintParams;
using vespalib::Issue;

using namespace search::fef;
using namespace search::fef::indexproperties::matchphase;
using namespace search::fef::indexproperties::matching;
using namespace search::fef::indexproperties;
using search::IDocumentMetaStore;
using search::StringStringMap;

namespace proton::matching {

namespace {

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
extractDegradationParams(const RankSetup &rankSetup, const Properties &rankProperties)
{
    return DegradationParams(DegradationAttribute::lookup(rankProperties, rankSetup.getDegradationAttribute()),
                             DegradationMaxHits::lookup(rankProperties, rankSetup.getDegradationMaxHits()),
                             !DegradationAscendingOrder::lookup(rankProperties, rankSetup.isDegradationOrderAscending()),
                             DegradationMaxFilterCoverage::lookup(rankProperties, rankSetup.getDegradationMaxFilterCoverage()),
                             DegradationSamplePercentage::lookup(rankProperties, rankSetup.getDegradationSamplePercentage()),
                             DegradationPostFilterMultiplier::lookup(rankProperties, rankSetup.getDegradationPostFilterMultiplier()));

}

DiversityParams
extractDiversityParams(const RankSetup &rankSetup, const Properties &rankProperties)
{
    return DiversityParams(DiversityAttribute::lookup(rankProperties, rankSetup.getDiversityAttribute()),
                           DiversityMinGroups::lookup(rankProperties, rankSetup.getDiversityMinGroups()),
                           DiversityCutoffFactor::lookup(rankProperties, rankSetup.getDiversityCutoffFactor()),
                           AttributeLimiter::toDiversityCutoffStrategy(DiversityCutoffStrategy::lookup(rankProperties, rankSetup.getDiversityCutoffStrategy())));
}

} // namespace proton::matching::<unnamed>

void
MatchTools::setup(search::fef::RankProgram::UP rank_program, double termwise_limit)
{
    if (_search) {
        _match_data->soft_reset();
    }
    _rank_program = std::move(rank_program);
    HandleRecorder recorder;
    {
        HandleRecorder::Binder bind(recorder);
        _rank_program->setup(*_match_data, _queryEnv, _featureOverrides);
    }
    bool can_reuse_search = (_search && !_search_has_changed &&
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
MatchTools::setup_first_phase()
{
    setup(_rankSetup.create_first_phase_program(),
          TermwiseLimit::lookup(_queryEnv.getProperties(), _rankSetup.get_termwise_limit()));
}

void
MatchTools::setup_second_phase()
{
    setup(_rankSetup.create_second_phase_program());
}

void
MatchTools::setup_match_features()
{
    setup(_rankSetup.create_match_program());
}

void
MatchTools::setup_summary()
{
    setup(_rankSetup.create_summary_program());
}

void
MatchTools::setup_dump()
{
    setup(_rankSetup.create_dump_program());
}

//-----------------------------------------------------------------------------

MatchToolsFactory::
MatchToolsFactory(QueryLimiter               & queryLimiter,
                  const vespalib::Doom       & doom,
                  ISearchContext             & searchContext,
                  IAttributeContext          & attributeContext,
                  search::engine::Trace      & root_trace,
                  vespalib::stringref          queryStack,
                  const vespalib::string     & location,
                  const ViewResolver         & viewResolver,
                  const IDocumentMetaStore   & metaStore,
                  const IIndexEnvironment    & indexEnv,
                  const RankSetup            & rankSetup,
                  const Properties           & rankProperties,
                  const Properties           & featureOverrides,
                  bool                         is_search)
    : _queryLimiter(queryLimiter),
      _global_filter_params(extract_global_filter_params(rankSetup, rankProperties, metaStore.getNumActiveLids(), searchContext.getDocIdLimit())),
      _requestContext(doom, attributeContext, rankProperties, _global_filter_params),
      _query(),
      _match_limiter(),
      _queryEnv(indexEnv, attributeContext, rankProperties, searchContext.getIndexes()),
      _mdl(),
      _rankSetup(rankSetup),
      _featureOverrides(featureOverrides),
      _diversityParams(),
      _valid(false)
{
    search::engine::Trace trace(root_trace.getRelativeTime(), root_trace.getLevel());
    trace.addEvent(4, "Start query setup");
    _query.setWhiteListBlueprint(metaStore.createWhiteListBlueprint());
    trace.addEvent(5, "Deserialize and build query tree");
    _valid = _query.buildTree(queryStack, location, viewResolver, indexEnv,
                              rankSetup.split_unpacking_iterators(),
                              rankSetup.delay_unpacking_iterators());
    if (_valid) {
        _query.extractTerms(_queryEnv.terms());
        _query.extractLocations(_queryEnv.locations());
        trace.addEvent(5, "Build query execution plan");
        _query.reserveHandles(_requestContext, searchContext, _mdl);
        trace.addEvent(5, "Optimize query execution plan");
        _query.optimize();
        trace.addEvent(4, "Perform dictionary lookups and posting lists initialization");
        _query.fetchPostings();
        if (is_search) {
            _query.handle_global_filter(searchContext.getDocIdLimit(),
                                        _global_filter_params.global_filter_lower_limit,
                                        _global_filter_params.global_filter_upper_limit,
                                        trace);
        }
        _query.freeze();
        trace.addEvent(5, "Prepare shared state for multi-threaded rank executors");
        _rankSetup.prepareSharedState(_queryEnv, _queryEnv.getObjectStore());
        _diversityParams = extractDiversityParams(_rankSetup, rankProperties);
        DegradationParams degradationParams = extractDegradationParams(_rankSetup, rankProperties);

        if (degradationParams.enabled()) {
            trace.addEvent(5, "Setup match phase limiter");
            _match_limiter = std::make_unique<MatchPhaseLimiter>(metaStore.getCommittedDocIdLimit(), searchContext.getAttributes(),
                                                                 _requestContext, degradationParams, _diversityParams);
        }
    }
    if ( ! _match_limiter) {
        _match_limiter = std::make_unique<NoMatchPhaseLimiter>();
    }
    trace.addEvent(4, "Complete query setup");
    if (root_trace.shouldTrace(4)) {
        vespalib::slime::ObjectInserter inserter(root_trace.createCursor("query_setup"), "traces");
        vespalib::slime::inject(trace.getTraces(), inserter);
    }
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
MatchToolsFactory::createDiversifier(uint32_t heapSize) const
{
    if ( !_diversityParams.enabled() ) {
        return std::unique_ptr<IDiversifier>();
    }
    auto attr = _requestContext.getAttribute(_diversityParams.attribute);
    if ( !attr) {
        Issue::report("Skipping diversity due to no %s attribute.", _diversityParams.attribute.c_str());
        return std::unique_ptr<IDiversifier>();
    }
    size_t max_per_group = heapSize/_diversityParams.min_groups;
    return DiversityFilter::create(*attr, heapSize, max_per_group, _diversityParams.min_groups,
                                   _diversityParams.cutoff_strategy == DiversityParams::CutoffStrategy::STRICT);
}

std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createTask(vespalib::stringref attribute, vespalib::stringref operation) const {
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
    return createTask(execute::onmatch::Attribute::lookup(_queryEnv.getProperties(), op._attribute),
                      execute::onmatch::Operation::lookup(_queryEnv.getProperties(), op._operation));
}
std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createOnSecondPhaseTask() const {
    const auto & op = _rankSetup.getMutateOnSecondPhase();
    return createTask(execute::onrerank::Attribute::lookup(_queryEnv.getProperties(), op._attribute),
                      execute::onrerank::Operation::lookup(_queryEnv.getProperties(), op._operation));
}
std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createOnSummaryTask() const {
    const auto & op = _rankSetup.getMutateOnSummary();
    return createTask(execute::onsummary::Attribute::lookup(_queryEnv.getProperties(), op._attribute),
                      execute::onsummary::Operation::lookup(_queryEnv.getProperties(), op._operation));
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

AttributeBlueprintParams
MatchToolsFactory::extract_global_filter_params(const search::fef::RankSetup& rank_setup,
                                                const search::fef::Properties& rank_properties,
                                                uint32_t active_docids,
                                                uint32_t docid_limit)
{
    double lower_limit = GlobalFilterLowerLimit::lookup(rank_properties, rank_setup.get_global_filter_lower_limit());
    double upper_limit = GlobalFilterUpperLimit::lookup(rank_properties, rank_setup.get_global_filter_upper_limit());

    // Note that we count the reserved docid 0 as active.
    // This ensures that when searchable-copies=1, the ratio is 1.0.
    double active_hit_ratio = std::min(active_docids + 1, docid_limit) / static_cast<double>(docid_limit);

    return {lower_limit * active_hit_ratio,
            upper_limit * active_hit_ratio};
}

AttributeOperationTask::AttributeOperationTask(const RequestContext & requestContext,
                                               vespalib::stringref attribute, vespalib::stringref operation)
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
