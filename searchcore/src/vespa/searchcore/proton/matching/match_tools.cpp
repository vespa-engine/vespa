// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_tools.h"
#include "querynodes.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchcore.matching.match_tools");
#include <vespa/searchlib/query/tree/querytreecreator.h>

using search::attribute::IAttributeContext;
using search::queryeval::IRequestContext;
using namespace search::fef;
using namespace search::fef::indexproperties::matchphase;
using search::IDocumentMetaStore;

namespace proton {
namespace matching {

namespace {

size_t
tagMatchData(const HandleRecorder::HandleSet & handles, MatchData & md)
{
    size_t ignored(0);
    for (TermFieldHandle handle(0); handle < md.getNumTermFields(); handle++) {
        if (handles.find(handle) == handles.end()) {
            md.resolveTermField(handle)->tagAsNotNeeded();
            ignored++;
        }
    }
    return ignored;
}

search::fef::RankProgram::UP setup_program(search::fef::RankProgram::UP program,
                                           const MatchDataLayout &mdl,
                                           const QueryEnvironment &queryEnv,
                                           const Properties &featureOverrides)
{
    HandleRecorder recorder;
    {
        HandleRecorder::Binder bind(recorder);
        program->setup(mdl, queryEnv, featureOverrides);
    }
    tagMatchData(recorder.getHandles(), program->match_data());
    return program;
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
      _mdl(mdl),
      _handleRecorder()
{
    HandleRecorder::Binder bind(_handleRecorder);
}

search::fef::RankProgram::UP
MatchTools::first_phase_program() const {
    auto program = setup_program(_rankSetup.create_first_phase_program(),
                                 _mdl, _queryEnv, _featureOverrides);
    program->match_data().set_termwise_limit(_rankSetup.get_termwise_limit());
    return program;
}

search::fef::RankProgram::UP
MatchTools::second_phase_program() const {
    return setup_program(_rankSetup.create_second_phase_program(),
                         _mdl, _queryEnv, _featureOverrides);
}

search::fef::RankProgram::UP
MatchTools::summary_program() const {
    return setup_program(_rankSetup.create_summary_program(),
                         _mdl, _queryEnv, _featureOverrides);
}

search::fef::RankProgram::UP
MatchTools::dump_program() const {
    return setup_program(_rankSetup.create_dump_program(),
                         _mdl, _queryEnv, _featureOverrides);
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
      _featureOverrides(featureOverrides)
{
    _valid = _query.buildTree(queryStack, location, viewResolver, indexEnv);
    if (_valid) {
        _query.extractTerms(_queryEnv.terms());
        _query.extractLocations(_queryEnv.locations());
        _query.setBlackListBlueprint(metaStore.createBlackListBlueprint());
        _query.reserveHandles(_requestContext, searchContext, _mdl);
        _query.optimize();
        _query.fetchPostings();
        _query.freeze();
        _rankSetup.prepareSharedState(_queryEnv, _queryEnv.getObjectStore());
        vespalib::string limit_attribute = DegradationAttribute::lookup(rankProperties);
        size_t limit_maxhits = DegradationMaxHits::lookup(rankProperties);
        bool limit_ascending = DegradationAscendingOrder::lookup(rankProperties);
        double limit_max_filter_coverage = DegradationMaxFilterCoverage::lookup(rankProperties);
        double samplePercentage = DegradationSamplePercentage::lookup(rankProperties);
        double postFilterMultiplier = DegradationPostFilterMultiplier::lookup(rankProperties);
        vespalib::string diversity_attribute = DiversityAttribute::lookup(rankProperties);
        uint32_t diversity_min_groups = DiversityMinGroups::lookup(rankProperties);
        double diversity_cutoff_factor = DiversityCutoffFactor::lookup(rankProperties);
        vespalib::string diversity_cutoff_strategy = DiversityCutoffStrategy::lookup(rankProperties);
        if (!limit_attribute.empty() && limit_maxhits > 0) {
            _match_limiter.reset(new MatchPhaseLimiter(metaStore.getCommittedDocIdLimit(), searchContext.getAttributes(), _requestContext,
                            limit_attribute, limit_maxhits, !limit_ascending, limit_max_filter_coverage,
                            samplePercentage, postFilterMultiplier,
                            diversity_attribute, diversity_min_groups,
                            diversity_cutoff_factor,
                            AttributeLimiter::toDiversityCutoffStrategy(diversity_cutoff_strategy)));
        } else if (_rankSetup.hasMatchPhaseDegradation()) {
            _match_limiter.reset(new MatchPhaseLimiter(metaStore.getCommittedDocIdLimit(), searchContext.getAttributes(), _requestContext,
                            _rankSetup.getDegradationAttribute(), _rankSetup.getDegradationMaxHits(), !_rankSetup.isDegradationOrderAscending(),
                            _rankSetup.getDegradationMaxFilterCoverage(),
                            _rankSetup.getDegradationSamplePercentage(), _rankSetup.getDegradationPostFilterMultiplier(),
                            _rankSetup.getDiversityAttribute(), _rankSetup.getDiversityMinGroups(),
                            _rankSetup.getDiversityCutoffFactor(),
                            AttributeLimiter::toDiversityCutoffStrategy(_rankSetup.getDiversityCutoffStrategy())));
        }
    }
    if (_match_limiter.get() == nullptr) {
        _match_limiter.reset(new NoMatchPhaseLimiter());
    }
}

MatchTools::UP
MatchToolsFactory::createMatchTools() const
{
    assert(_valid);
    return MatchTools::UP(
            new MatchTools(_queryLimiter, _requestContext.getSoftDoom(), _hardDoom, _query, *_match_limiter, _queryEnv,
                           _mdl, _rankSetup, _featureOverrides));
}

} // namespace matching
} // namespace proton
