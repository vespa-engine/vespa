// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ranksetup.h"
#include "indexproperties.h"
#include "featurenameparser.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/asciistream.h>

using vespalib::make_string_short::fmt;

namespace {
class VisitorAdapter : public search::fef::IDumpFeatureVisitor
{
    search::fef::BlueprintResolver &_resolver;
public:
    explicit VisitorAdapter(search::fef::BlueprintResolver &resolver)
        : _resolver(resolver) {}
    void visitDumpFeature(const vespalib::string &name) override {
        _resolver.addSeed(name);
    }
};
} // namespace <unnamed>

namespace search::fef {

using namespace indexproperties;

RankSetup::RankSetup(const BlueprintFactory &factory, const IIndexEnvironment &indexEnv)
    : _factory(factory),
      _indexEnv(indexEnv),
      _first_phase_resolver(std::make_shared<BlueprintResolver>(factory, indexEnv)),
      _second_phase_resolver(std::make_shared<BlueprintResolver>(factory, indexEnv)),
      _match_resolver(std::make_shared<BlueprintResolver>(factory, indexEnv)),
      _summary_resolver(std::make_shared<BlueprintResolver>(factory, indexEnv)),
      _dumpResolver(std::make_shared<BlueprintResolver>(factory, indexEnv)),
      _firstPhaseRankFeature(),
      _secondPhaseRankFeature(),
      _degradationAttribute(),
      _termwise_limit(1.0),
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
      _match_features(),
      _summaryFeatures(),
      _dumpFeatures(),
      _warnings(),
      _feature_rename_map(),
      _ignoreDefaultRankFeatures(false),
      _compiled(false),
      _compileError(false),
      _degradationAscendingOrder(false),
      _diversityAttribute(),
      _diversityMinGroups(1),
      _diversityCutoffFactor(10.0),
      _diversityCutoffStrategy("loose"),
      _softTimeoutEnabled(false),
      _softTimeoutTailCost(0.1),
      _softTimeoutFactor(0.5),
      _global_filter_lower_limit(0.0),
      _global_filter_upper_limit(1.0),
      _mutateOnMatch(),
      _mutateOnFirstPhase(),
      _mutateOnSecondPhase(),
      _mutateOnSummary(),
      _mutateAllowQueryOverride(false)
{ }

RankSetup::~RankSetup() = default;

void
RankSetup::configure()
{
    setFirstPhaseRank(rank::FirstPhase::lookup(_indexEnv.getProperties()));
    setSecondPhaseRank(rank::SecondPhase::lookup(_indexEnv.getProperties()));
    for (const auto &feature: match::Feature::lookup(_indexEnv.getProperties())) {
        add_match_feature(feature);
    }
    std::vector<vespalib::string> summaryFeatures = summary::Feature::lookup(_indexEnv.getProperties());
    for (const auto & feature : summaryFeatures) {
        addSummaryFeature(feature);
    }
    setIgnoreDefaultRankFeatures(dump::IgnoreDefaultFeatures::check(_indexEnv.getProperties()));
    std::vector<vespalib::string> dumpFeatures = dump::Feature::lookup(_indexEnv.getProperties());
    for (const auto & feature : dumpFeatures) {
        addDumpFeature(feature);
    }
    for (const auto & rename : feature_rename::Rename::lookup(_indexEnv.getProperties())) {
        _feature_rename_map[rename.first] = rename.second;
    }
    set_termwise_limit(matching::TermwiseLimit::lookup(_indexEnv.getProperties()));
    setNumThreadsPerSearch(matching::NumThreadsPerSearch::lookup(_indexEnv.getProperties()));
    setMinHitsPerThread(matching::MinHitsPerThread::lookup(_indexEnv.getProperties()));
    setNumSearchPartitions(matching::NumSearchPartitions::lookup(_indexEnv.getProperties()));
    setHeapSize(hitcollector::HeapSize::lookup(_indexEnv.getProperties()));
    setArraySize(hitcollector::ArraySize::lookup(_indexEnv.getProperties()));
    setDegradationAttribute(matchphase::DegradationAttribute::lookup(_indexEnv.getProperties()));
    setDegradationOrderAscending(matchphase::DegradationAscendingOrder::lookup(_indexEnv.getProperties()));
    setDegradationMaxHits(matchphase::DegradationMaxHits::lookup(_indexEnv.getProperties()));
    setDegradationMaxFilterCoverage(matchphase::DegradationMaxFilterCoverage::lookup(_indexEnv.getProperties()));
    setDegradationSamplePercentage(matchphase::DegradationSamplePercentage::lookup(_indexEnv.getProperties()));
    setDegradationPostFilterMultiplier(matchphase::DegradationPostFilterMultiplier::lookup(_indexEnv.getProperties()));
    setDiversityAttribute(matchphase::DiversityAttribute::lookup(_indexEnv.getProperties()));
    setDiversityMinGroups(matchphase::DiversityMinGroups::lookup(_indexEnv.getProperties()));
    setDiversityCutoffFactor(matchphase::DiversityCutoffFactor::lookup(_indexEnv.getProperties()));
    setDiversityCutoffStrategy(matchphase::DiversityCutoffStrategy::lookup(_indexEnv.getProperties()));
    setEstimatePoint(hitcollector::EstimatePoint::lookup(_indexEnv.getProperties()));
    setEstimateLimit(hitcollector::EstimateLimit::lookup(_indexEnv.getProperties()));
    setRankScoreDropLimit(hitcollector::RankScoreDropLimit::lookup(_indexEnv.getProperties()));
    setSoftTimeoutEnabled(softtimeout::Enabled::lookup(_indexEnv.getProperties()));
    setSoftTimeoutTailCost(softtimeout::TailCost::lookup(_indexEnv.getProperties()));
    setSoftTimeoutFactor(softtimeout::Factor::lookup(_indexEnv.getProperties()));
    set_global_filter_lower_limit(matching::GlobalFilterLowerLimit::lookup(_indexEnv.getProperties()));
    set_global_filter_upper_limit(matching::GlobalFilterUpperLimit::lookup(_indexEnv.getProperties()));
    _mutateOnMatch._attribute = mutate::on_match::Attribute::lookup(_indexEnv.getProperties());
    _mutateOnMatch._operation = mutate::on_match::Operation::lookup(_indexEnv.getProperties());
    _mutateOnFirstPhase._attribute = mutate::on_first_phase::Attribute::lookup(_indexEnv.getProperties());
    _mutateOnFirstPhase._operation = mutate::on_first_phase::Operation::lookup(_indexEnv.getProperties());
    _mutateOnSecondPhase._attribute = mutate::on_second_phase::Attribute::lookup(_indexEnv.getProperties());
    _mutateOnSecondPhase._operation = mutate::on_second_phase::Operation::lookup(_indexEnv.getProperties());
    _mutateOnSummary._attribute = mutate::on_summary::Attribute::lookup(_indexEnv.getProperties());
    _mutateOnSummary._operation = mutate::on_summary::Operation::lookup(_indexEnv.getProperties());
    _mutateAllowQueryOverride = mutate::AllowQueryOverride::check(_indexEnv.getProperties());
}

void
RankSetup::setFirstPhaseRank(const vespalib::string &featureName)
{
    assert(!_compiled);
    _firstPhaseRankFeature = featureName;
}

void
RankSetup::setSecondPhaseRank(const vespalib::string &featureName)
{
    assert(!_compiled);
    _secondPhaseRankFeature = featureName;
}

void
RankSetup::add_match_feature(const vespalib::string &match_feature)
{
    assert(!_compiled);
    _match_features.push_back(match_feature);
}

void
RankSetup::addSummaryFeature(const vespalib::string &summaryFeature)
{
    assert(!_compiled);
    _summaryFeatures.push_back(summaryFeature);
}

void
RankSetup::addDumpFeature(const vespalib::string &dumpFeature)
{
    assert(!_compiled);
    _dumpFeatures.push_back(dumpFeature);
}

void
RankSetup::compileAndCheckForErrors(BlueprintResolver &bpr) {
    bool ok = bpr.compile();
    if ( ! ok ) {
        _compileError = true;
        const auto & warnings = bpr.getWarnings();
        _warnings.insert(_warnings.end(), warnings.begin(), warnings.end());
    }
}
bool
RankSetup::compile()
{
    assert(!_compiled);
    if (!_firstPhaseRankFeature.empty()) {
        FeatureNameParser parser(_firstPhaseRankFeature);
        if (parser.valid()) {
            _firstPhaseRankFeature = parser.featureName();
            _first_phase_resolver->addSeed(_firstPhaseRankFeature);
        } else {
            vespalib::string e = fmt("invalid feature name for initial rank: '%s'", _firstPhaseRankFeature.c_str());
            _warnings.emplace_back(e);
            _compileError = true;
        }
    }
    if (!_secondPhaseRankFeature.empty()) {
        FeatureNameParser parser(_secondPhaseRankFeature);
        if (parser.valid()) {
            _secondPhaseRankFeature = parser.featureName();
            _second_phase_resolver->addSeed(_secondPhaseRankFeature);
        } else {
            vespalib::string e = fmt("invalid feature name for final rank: '%s'", _secondPhaseRankFeature.c_str());
            _warnings.emplace_back(e);
            _compileError = true;
        }
    }
    for (const auto &feature: _match_features) {
        _match_resolver->addSeed(feature);
    }
    for (const auto & feature :_summaryFeatures) {
        _summary_resolver->addSeed(feature);
    }
    if (!_ignoreDefaultRankFeatures) {
        VisitorAdapter adapter(*_dumpResolver);
        _factory.visitDumpFeatures(_indexEnv, adapter);
    }
    for (const auto & feature : _dumpFeatures) {
        _dumpResolver->addSeed(feature);
    }
    _indexEnv.hintFeatureMotivation(IIndexEnvironment::RANK);
    compileAndCheckForErrors(*_first_phase_resolver);
    compileAndCheckForErrors(*_second_phase_resolver);
    compileAndCheckForErrors(*_match_resolver);
    compileAndCheckForErrors(*_summary_resolver);
    _indexEnv.hintFeatureMotivation(IIndexEnvironment::DUMP);
    compileAndCheckForErrors(*_dumpResolver);
    _compiled = true;
    return !_compileError;
}

void
RankSetup::prepareSharedState(const IQueryEnvironment &queryEnv, IObjectStore &objectStore) const
{
    assert(_compiled && !_compileError);
    for (const auto &spec : _first_phase_resolver->getExecutorSpecs()) {
        spec.blueprint->prepareSharedState(queryEnv, objectStore);
    }
    for (const auto &spec : _second_phase_resolver->getExecutorSpecs()) {
        spec.blueprint->prepareSharedState(queryEnv, objectStore);
    }
    for (const auto &spec : _match_resolver->getExecutorSpecs()) {
        spec.blueprint->prepareSharedState(queryEnv, objectStore);
    }
    for (const auto &spec : _summary_resolver->getExecutorSpecs()) {
        spec.blueprint->prepareSharedState(queryEnv, objectStore);
    }
}

vespalib::string
RankSetup::getJoinedWarnings() const {
    vespalib::asciistream os;
    for (const auto & m : _warnings) {
        os << m << "\n";
    }
    return os.str();
}

}
