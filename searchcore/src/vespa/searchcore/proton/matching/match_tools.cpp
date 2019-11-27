// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_tools.h"
#include "querynodes.h"
#include <vespa/searchcorespi/index/indexsearchable.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/attribute/diversity.h>
#include <vespa/searchlib/attribute/attribute_operation.h>
#include <vespa/searchlib/common/bitvector.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.match_tools");

using search::attribute::IAttributeContext;
using search::queryeval::IRequestContext;
using search::queryeval::IDiversifier;
using search::attribute::diversity::DiversityFilter;
using search::attribute::BasicType;

using namespace search::fef;
using namespace search::fef::indexproperties::matchphase;
using namespace search::fef::indexproperties::matching;
using namespace search::fef::indexproperties;
using search::IDocumentMetaStore;

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
        _used_handles = recorder.get_handles();
        _search_has_changed = false;
    }
}

MatchTools::MatchTools(QueryLimiter & queryLimiter,
                       const vespalib::Doom & softDoom,
                       const vespalib::Doom & hardDoom,
                       const Query &query,
                       MaybeMatchPhaseLimiter & match_limiter_in,
                       const QueryEnvironment & queryEnv,
                       const MatchDataLayout & mdl,
                       const RankSetup & rankSetup,
                       const Properties & featureOverrides)
    : _queryLimiter(queryLimiter),
      _softDoom(softDoom),
      _hardDoom(hardDoom),
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
                  const vespalib::Doom       & softDoom,
                  const vespalib::Doom       & hardDoom,
                  ISearchContext             & searchContext,
                  IAttributeContext          & attributeContext,
                  vespalib::stringref          queryStack,
                  const vespalib::string     & location,
                  const ViewResolver         & viewResolver,
                  const IDocumentMetaStore   & metaStore,
                  const IIndexEnvironment    & indexEnv,
                  const RankSetup            & rankSetup,
                  const Properties           & rankProperties,
                  const Properties           & featureOverrides)
    : _queryLimiter(queryLimiter),
      _requestContext(softDoom, attributeContext, rankProperties),
      _hardDoom(hardDoom),
      _query(),
      _match_limiter(),
      _queryEnv(indexEnv, attributeContext, rankProperties, searchContext.getIndexes()),
      _mdl(),
      _rankSetup(rankSetup),
      _featureOverrides(featureOverrides),
      _diversityParams(),
      _valid(false)
{
    _query.setWhiteListBlueprint(metaStore.createWhiteListBlueprint());
    _valid = _query.buildTree(queryStack, location, viewResolver, indexEnv,
                              rankSetup.split_unpacking_iterators(),
                              rankSetup.delay_unpacking_iterators());
    if (_valid) {
        _query.extractTerms(_queryEnv.terms());
        _query.extractLocations(_queryEnv.locations());
        _query.reserveHandles(_requestContext, searchContext, _mdl);
        _query.optimize();
        _query.fetchPostings();
        _query.freeze();
        _rankSetup.prepareSharedState(_queryEnv, _queryEnv.getObjectStore());
        _diversityParams = extractDiversityParams(_rankSetup, rankProperties);
        DegradationParams degradationParams = extractDegradationParams(_rankSetup, rankProperties);

        if (degradationParams.enabled()) {
            _match_limiter = std::make_unique<MatchPhaseLimiter>(metaStore.getCommittedDocIdLimit(), searchContext.getAttributes(),
                                                                 _requestContext, degradationParams, _diversityParams);
        }
    }
    if ( ! _match_limiter) {
        _match_limiter = std::make_unique<NoMatchPhaseLimiter>();
    }
}

MatchToolsFactory::~MatchToolsFactory() = default;

MatchTools::UP
MatchToolsFactory::createMatchTools() const
{
    assert(_valid);
    return std::make_unique<MatchTools>(_queryLimiter, _requestContext.getSoftDoom(), _hardDoom, _query,
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
        LOG(warning, "Skipping diversity due to no %s attribute.", _diversityParams.attribute.c_str());
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
    return createTask(execute::onmatch::Attribute::lookup(_queryEnv.getProperties()),
                      execute::onmatch::Operation::lookup(_queryEnv.getProperties()));
}
std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createOnReRankTask() const {
    return createTask(execute::onrerank::Attribute::lookup(_queryEnv.getProperties()),
                      execute::onrerank::Operation::lookup(_queryEnv.getProperties()));
}
std::unique_ptr<AttributeOperationTask>
MatchToolsFactory::createOnSummaryTask() const {
    return createTask(execute::onsummary::Attribute::lookup(_queryEnv.getProperties()),
                      execute::onsummary::Operation::lookup(_queryEnv.getProperties()));
}

bool
MatchToolsFactory::has_first_phase_rank() const {
    return !_rankSetup.getFirstPhaseRank().empty();
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
