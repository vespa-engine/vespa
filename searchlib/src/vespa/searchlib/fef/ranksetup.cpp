// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fef.ranksetup");
#include "ranksetup.h"
#include "idumpfeaturevisitor.h"
#include "indexproperties.h"
#include "featurenameparser.h"

namespace {
class VisitorAdapter : public search::fef::IDumpFeatureVisitor
{
    search::fef::BlueprintResolver &_resolver;
public:
    VisitorAdapter(search::fef::BlueprintResolver &resolver)
        : _resolver(resolver) {}
    virtual void visitDumpFeature(const vespalib::string &name) {
        _resolver.addSeed(name);
    }
};
} // namespace <unnamed>

namespace search {
namespace fef {

RankSetup::RankSetup(const BlueprintFactory &factory, const IIndexEnvironment &indexEnv)
    : _factory(factory),
      _indexEnv(indexEnv),
      _first_phase_resolver(new BlueprintResolver(factory, indexEnv)),
      _second_phase_resolver(new BlueprintResolver(factory, indexEnv)),
      _summary_resolver(new BlueprintResolver(factory, indexEnv)),
      _dumpResolver(new BlueprintResolver(factory, indexEnv)),
      _firstPhaseRankFeature(),
      _secondPhaseRankFeature(),
      _degradationAttribute(),
      _numThreads(0),
      _minHitsPerThread(0),
      _numSearchPartitions(0),
      _heapSize(0),
      _arraySize(0),
      _estimatePoint(0),
      _estimateLimit(0),
      _degradationMaxHits(0),
      _degradationMaxFilterCoverage(1.0),
      _degradationSamplePercentage(0.2),
      _degradationPostFilterMultiplier(1.0),
      _rankScoreDropLimit(0),
      _summaryFeatures(),
      _dumpFeatures(),
      _ignoreDefaultRankFeatures(false),
      _compiled(false),
      _compileError(false),
      _degradationAscendingOrder(false),
      _diversityAttribute(),
      _diversityMinGroups(1),
      _diversityCutoffFactor(10.0),
      _diversityCutoffStrategy("loose"),
      _softTimeoutEnabled(false),
      _softTimeoutTailCost(0.1)
{ }

RankSetup::~RankSetup() { }

void
RankSetup::configure()
{
    setFirstPhaseRank(indexproperties::rank::FirstPhase::lookup(_indexEnv.getProperties()));
    setSecondPhaseRank(indexproperties::rank::SecondPhase::lookup(_indexEnv.getProperties()));
    std::vector<vespalib::string> summaryFeatures = indexproperties::summary::Feature::lookup(_indexEnv.getProperties());
    for (uint32_t i = 0; i < summaryFeatures.size(); ++i) {
        addSummaryFeature(summaryFeatures[i]);
    }
    setIgnoreDefaultRankFeatures(indexproperties::dump::IgnoreDefaultFeatures::check(_indexEnv.getProperties()));
    std::vector<vespalib::string> dumpFeatures = indexproperties::dump::Feature::lookup(_indexEnv.getProperties());
    for (uint32_t i = 0; i < dumpFeatures.size(); ++i) {
        addDumpFeature(dumpFeatures[i]);
    }
    set_termwise_limit(indexproperties::matching::TermwiseLimit::lookup(_indexEnv.getProperties()));
    setNumThreadsPerSearch(indexproperties::matching::NumThreadsPerSearch::lookup(_indexEnv.getProperties()));
    setMinHitsPerThread(indexproperties::matching::MinHitsPerThread::lookup(_indexEnv.getProperties()));
    setNumSearchPartitions(indexproperties::matching::NumSearchPartitions::lookup(_indexEnv.getProperties()));
    setHeapSize(indexproperties::hitcollector::HeapSize::lookup(_indexEnv.getProperties()));
    setArraySize(indexproperties::hitcollector::ArraySize::lookup(_indexEnv.getProperties()));
    setDegradationAttribute(indexproperties::matchphase::DegradationAttribute::lookup(_indexEnv.getProperties()));
    setDegradationOrderAscending(indexproperties::matchphase::DegradationAscendingOrder::lookup(_indexEnv.getProperties()));
    setDegradationMaxHits(indexproperties::matchphase::DegradationMaxHits::lookup(_indexEnv.getProperties()));
    setDegradationMaxFilterCoverage(indexproperties::matchphase::DegradationMaxFilterCoverage::lookup(_indexEnv.getProperties()));
    setDegradationSamplePercentage(indexproperties::matchphase::DegradationSamplePercentage::lookup(_indexEnv.getProperties()));
    setDegradationPostFilterMultiplier(indexproperties::matchphase::DegradationPostFilterMultiplier::lookup(_indexEnv.getProperties()));
    setDiversityAttribute(indexproperties::matchphase::DiversityAttribute::lookup(_indexEnv.getProperties()));
    setDiversityMinGroups(indexproperties::matchphase::DiversityMinGroups::lookup(_indexEnv.getProperties()));
    setDiversityCutoffFactor(indexproperties::matchphase::DiversityCutoffFactor::lookup(_indexEnv.getProperties()));
    setDiversityCutoffStrategy(indexproperties::matchphase::DiversityCutoffStrategy::lookup(_indexEnv.getProperties()));
    setEstimatePoint(indexproperties::hitcollector::EstimatePoint::lookup(_indexEnv.getProperties()));
    setEstimateLimit(indexproperties::hitcollector::EstimateLimit::lookup(_indexEnv.getProperties()));
    setRankScoreDropLimit(indexproperties::hitcollector::RankScoreDropLimit::lookup(_indexEnv.getProperties()));
    setSoftTimeoutEnabled(indexproperties::softtimeout::Enabled::lookup(_indexEnv.getProperties()));
    setSoftTimeoutTailCost(indexproperties::softtimeout::TailCost::lookup(_indexEnv.getProperties()));
}

void
RankSetup::setFirstPhaseRank(const vespalib::string &featureName)
{
    LOG_ASSERT(!_compiled);
    _firstPhaseRankFeature = featureName;
}

void
RankSetup::setSecondPhaseRank(const vespalib::string &featureName)
{
    LOG_ASSERT(!_compiled);
    _secondPhaseRankFeature = featureName;
}

void
RankSetup::addSummaryFeature(const vespalib::string &summaryFeature)
{
    LOG_ASSERT(!_compiled);
    _summaryFeatures.push_back(summaryFeature);
}

void
RankSetup::addDumpFeature(const vespalib::string &dumpFeature)
{
    LOG_ASSERT(!_compiled);
    _dumpFeatures.push_back(dumpFeature);
}

bool
RankSetup::compile()
{
    LOG_ASSERT(!_compiled);
    if (!_firstPhaseRankFeature.empty()) {
        FeatureNameParser parser(_firstPhaseRankFeature);
        if (parser.valid()) {
            _firstPhaseRankFeature = parser.featureName();
            _first_phase_resolver->addSeed(_firstPhaseRankFeature);
        } else {
            LOG(warning, "invalid feature name for initial rank: '%s'",
                _firstPhaseRankFeature.c_str());
            _compileError = true;
        }
    }
    if (!_secondPhaseRankFeature.empty()) {
        FeatureNameParser parser(_secondPhaseRankFeature);
        if (parser.valid()) {
            _secondPhaseRankFeature = parser.featureName();
            _second_phase_resolver->addSeed(_secondPhaseRankFeature);
        } else {
            LOG(warning, "invalid feature name for final rank: '%s'",
                _secondPhaseRankFeature.c_str());
            _compileError = true;
        }
    }
    for (uint32_t i = 0; i < _summaryFeatures.size(); ++i) {
        _summary_resolver->addSeed(_summaryFeatures[i]);
    }
    if (!_ignoreDefaultRankFeatures) {
        VisitorAdapter adapter(*_dumpResolver);
        _factory.visitDumpFeatures(_indexEnv, adapter);
    }
    for (uint32_t i = 0; i < _dumpFeatures.size(); ++i) {
        _dumpResolver->addSeed(_dumpFeatures[i]);
    }
    _indexEnv.hintFeatureMotivation(IIndexEnvironment::RANK);
    _compileError |= !_first_phase_resolver->compile();
    _compileError |= !_second_phase_resolver->compile();
    _compileError |= !_summary_resolver->compile();
    _indexEnv.hintFeatureMotivation(IIndexEnvironment::DUMP);
    _compileError |= !_dumpResolver->compile();
    _compiled = true;
    return !_compileError;
}

void
RankSetup::prepareSharedState(const IQueryEnvironment &queryEnv, IObjectStore &objectStore) const
{
    LOG_ASSERT(_compiled && !_compileError);
    for (const auto &spec : _first_phase_resolver->getExecutorSpecs()) {
        spec.blueprint->prepareSharedState(queryEnv, objectStore);
    }
    for (const auto &spec : _second_phase_resolver->getExecutorSpecs()) {
        spec.blueprint->prepareSharedState(queryEnv, objectStore);
    }
    for (const auto &spec : _summary_resolver->getExecutorSpecs()) {
        spec.blueprint->prepareSharedState(queryEnv, objectStore);
    }
}

} // namespace fef
} // namespace search
