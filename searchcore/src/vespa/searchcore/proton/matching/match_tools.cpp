// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_tools.h"
#include "querynodes.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/attribute/diversity.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.match_tools");
#include <vespa/searchlib/query/tree/querytreecreator.h>

using search::attribute::IAttributeContext;
using search::queryeval::IRequestContext;
using search::queryeval::IDiversifier;
using search::attribute::diversity::DiversityFilter;

using namespace search::fef;
using namespace search::fef::indexproperties::matchphase;
using namespace search::fef::indexproperties::matching;
using search::IDocumentMetaStore;

namespace proton::matching {

namespace {

bool contains_all(const HandleRecorder::HandleSet &old_set,
                  const HandleRecorder::HandleSet &new_set)
{
    for (TermFieldHandle handle: new_set) {
        if (old_set.find(handle) == old_set.end()) {
            return false;
        }
    }
    return true;
}

void tag_match_data(const HandleRecorder::HandleSet &handles, MatchData &match_data) {
    for (TermFieldHandle handle = 0; handle < match_data.getNumTermFields(); ++handle) {
        if (handles.find(handle) == handles.end()) {
            match_data.resolveTermField(handle)->tagAsNotNeeded();
        }
    }
}

std::unique_ptr<DegradationParams>
getDegradationParams(const RankSetup & rankSetup, const Properties & rankProperties)
{
    vespalib::string limit_attribute = DegradationAttribute::lookup(rankProperties);
    size_t limit_maxhits = DegradationMaxHits::lookup(rankProperties);
    if (!limit_attribute.empty() && limit_maxhits > 0) {
        bool limit_ascending = DegradationAscendingOrder::lookup(rankProperties);
        double limit_max_filter_coverage = DegradationMaxFilterCoverage::lookup(rankProperties);
        double samplePercentage = DegradationSamplePercentage::lookup(rankProperties);
        double postFilterMultiplier = DegradationPostFilterMultiplier::lookup(rankProperties);
        return std::make_unique<DegradationParams>(limit_attribute, limit_maxhits, !limit_ascending,
                                                   limit_max_filter_coverage, samplePercentage, postFilterMultiplier);
    } else if (rankSetup.hasMatchPhaseDegradation()) {
        return std::make_unique<DegradationParams>(rankSetup.getDegradationAttribute(), rankSetup.getDegradationMaxHits(),
                                                   !rankSetup.isDegradationOrderAscending(), rankSetup.getDegradationMaxFilterCoverage(),
                                                   rankSetup.getDegradationSamplePercentage(), rankSetup.getDegradationPostFilterMultiplier());
    }
    return std::unique_ptr<DegradationParams>();
}

DiversityParams
getDiversityParams(const RankSetup & rankSetup, const Properties & rankProperties)
{
    vespalib::string diversity_attribute = DiversityAttribute::lookup(rankProperties);

    return (!diversity_attribute.empty())
        ? DiversityParams(diversity_attribute, DiversityMinGroups::lookup(rankProperties),
                          DiversityCutoffFactor::lookup(rankProperties),
                          AttributeLimiter::toDiversityCutoffStrategy(DiversityCutoffStrategy::lookup(rankProperties)))
        : DiversityParams(rankSetup.getDiversityAttribute(), rankSetup.getDiversityMinGroups(),
                          rankSetup.getDiversityCutoffFactor(),
                          AttributeLimiter::toDiversityCutoffStrategy(rankSetup.getDiversityCutoffStrategy()));
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
                             contains_all(_used_handles, recorder.getHandles()));
    if (!can_reuse_search) {
        tag_match_data(recorder.getHandles(), *_match_data);
        _match_data->set_termwise_limit(termwise_limit);
        _search = _query.createSearch(*_match_data);
        _used_handles = recorder.getHandles();
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
                  const vespalib::stringref  & queryStack,
                  const vespalib::string     & location,
                  const ViewResolver         & viewResolver,
                  const IDocumentMetaStore   & metaStore,
                  const IIndexEnvironment    & indexEnv,
                  const RankSetup            & rankSetup,
                  const Properties           & rankProperties,
                  const Properties           & featureOverrides)
    : _queryLimiter(queryLimiter),
      _requestContext(softDoom, attributeContext),
      _hardDoom(hardDoom),
      _query(),
      _match_limiter(),
      _queryEnv(indexEnv, attributeContext, rankProperties),
      _mdl(),
      _rankSetup(rankSetup),
      _featureOverrides(featureOverrides),
      _diversityParams(),
      _valid(_query.buildTree(queryStack, location, viewResolver, indexEnv))
{
    if (_valid) {
        _query.extractTerms(_queryEnv.terms());
        _query.extractLocations(_queryEnv.locations());
        _query.setWhiteListBlueprint(metaStore.createWhiteListBlueprint());
        _query.reserveHandles(_requestContext, searchContext, _mdl);
        _query.optimize();
        _query.fetchPostings();
        _query.freeze();
        _rankSetup.prepareSharedState(_queryEnv, _queryEnv.getObjectStore());
        _diversityParams = getDiversityParams(_rankSetup, rankProperties);
        std::unique_ptr<DegradationParams> degradationParams = getDegradationParams(_rankSetup, rankProperties);

        if (degradationParams) {
            _match_limiter = std::make_unique<MatchPhaseLimiter>(metaStore.getCommittedDocIdLimit(), searchContext.getAttributes(),
                                                                 _requestContext, *degradationParams, _diversityParams);
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

std::unique_ptr<IDiversifier> MatchToolsFactory::createDiversifier() const
{
    if (_diversityParams._attribute.empty()) {
        return std::unique_ptr<IDiversifier>();
    }
    auto attr = _requestContext.getAttribute(_diversityParams._attribute);
    if ( !attr) {
        LOG(warning, "Skipping diversity due to no %s attribute.", _diversityParams._attribute.c_str());
        return std::unique_ptr<IDiversifier>();
    }
    size_t max_per_group = _rankSetup.getHeapSize()/_diversityParams._min_groups;
    return DiversityFilter::create(*attr, _rankSetup.getHeapSize(), max_per_group, _diversityParams._min_groups,
                                   _diversityParams._cutoff_strategy == DiversityParams::CutoffStrategy::STRICT);
}

}
