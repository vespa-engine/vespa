// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexproperties.h"
#include "properties.h"
#include <vespa/vespalib/locale/c.h>
#include <limits>
#include <charconv>

namespace search::fef::indexproperties {

namespace {

std::string
lookupString(const Properties &props, const std::string &name,
             const std::string &defaultValue)
{
    Property p = props.lookup(name);
    if (p.found()) {
        return p.get();
    }
    return defaultValue;
}

std::vector<std::string>
lookupStringVector(const Properties &props, const std::string &name,
                   const std::vector<std::string> &defaultValue)
{
    Property p = props.lookup(name);
    if (p.found()) {
        std::vector<std::string> retval;
        for (uint32_t i = 0; i < p.size(); ++i) {
            retval.push_back(p.getAt(i));
        }
        return retval;
    }
    return defaultValue;
}

std::optional<double>
lookup_opt_double(const Properties &props, std::string_view name, std::optional<double> default_value)
{
    Property p = props.lookup(name);
    if (p.found()) {
        return vespalib::locale::c::strtod(p.get().c_str(), nullptr);
    }
    return default_value;
}

double
lookupDouble(const Properties &props, const std::string &name, double defaultValue)
{
    return lookup_opt_double(props, name, defaultValue).value();
}

uint32_t
lookupUint32(const Properties &props, const std::string &name, uint32_t defaultValue)
{
    Property p = props.lookup(name);
    uint32_t value(defaultValue);
    if (p.found()) {
        const auto & valS = p.get();
        const char * start = valS.c_str();
        const char * end = start + valS.size();
        while ((start != end) && std::isspace(static_cast<unsigned char>(start[0]))) { start++; }
        std::from_chars(start, end, value);
    }
    return value;
}

bool
lookupBool(const Properties &props, const std::string &name, bool defaultValue)
{
    Property p = props.lookup(name);
    if (p.found()) {
        return (p.get() == "true");
    }
    return defaultValue;
}

bool
checkIfTrue(const Properties &props, const std::string &name,
            const std::string &defaultValue)
{
    return (props.lookup(name).get(defaultValue) == "true");
}

}

namespace eval {

const std::string LazyExpressions::NAME("vespa.eval.lazy_expressions");

bool
LazyExpressions::check(const Properties &props, bool default_value)
{
    return lookupBool(props, NAME, default_value);
}

const std::string UseFastForest::NAME("vespa.eval.use_fast_forest");
const bool UseFastForest::DEFAULT_VALUE(false);
bool UseFastForest::check(const Properties &props) { return lookupBool(props, NAME, DEFAULT_VALUE); }

} // namespace eval

namespace rank {

const std::string FirstPhase::NAME("vespa.rank.firstphase");
const std::string FirstPhase::DEFAULT_VALUE("nativeRank");

std::string
FirstPhase::lookup(const Properties &props)
{
    return lookupString(props, NAME, DEFAULT_VALUE);
}

const std::string SecondPhase::NAME("vespa.rank.secondphase");
const std::string SecondPhase::DEFAULT_VALUE("");

std::string
SecondPhase::lookup(const Properties &props)
{
    return lookupString(props, NAME, DEFAULT_VALUE);
}

} // namespace rank

namespace execute {

namespace onmatch {

    const std::string Attribute::NAME("vespa.execute.onmatch.attribute");
    const std::string Attribute::DEFAULT_VALUE("");
    const std::string Operation::NAME("vespa.execute.onmatch.operation");
    const std::string Operation::DEFAULT_VALUE("");

    std::string
    Attribute::lookup(const Properties &props, const std::string & defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

    std::string
    Operation::lookup(const Properties &props, const std::string & defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

}
namespace onrerank {

    const std::string Attribute::NAME("vespa.execute.onrerank.attribute");
    const std::string Attribute::DEFAULT_VALUE("");
    const std::string Operation::NAME("vespa.execute.onrerank.operation");
    const std::string Operation::DEFAULT_VALUE("");

    std::string
    Attribute::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

    std::string
    Operation::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

}

namespace onsummary {

    const std::string Attribute::NAME("vespa.execute.onsummary.attribute");
    const std::string Attribute::DEFAULT_VALUE("");
    const std::string Operation::NAME("vespa.execute.onsummary.operation");
    const std::string Operation::DEFAULT_VALUE("");

    std::string
    Attribute::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

    std::string
    Operation::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

}
}

namespace temporary {

}

namespace mutate {

const std::string AllowQueryOverride::NAME("vespa.mutate.allow_query_override");
bool AllowQueryOverride::check(const Properties &props) {
    return lookupBool(props, NAME, false);
}

namespace on_match {

    const std::string Attribute::NAME("vespa.mutate.on_match.attribute");
    const std::string Attribute::DEFAULT_VALUE("");
    const std::string Operation::NAME("vespa.mutate.on_match.operation");
    const std::string Operation::DEFAULT_VALUE("");

    std::string
    Attribute::lookup(const Properties &props, const std::string & defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

    std::string
    Operation::lookup(const Properties &props, const std::string & defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

}
namespace on_first_phase {

    const std::string Attribute::NAME("vespa.mutate.on_first_phase.attribute");
    const std::string Attribute::DEFAULT_VALUE("");
    const std::string Operation::NAME("vespa.mutate.on_first_phase.operation");
    const std::string Operation::DEFAULT_VALUE("");

    std::string
    Attribute::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

    std::string
    Operation::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

}
namespace on_second_phase {

    const std::string Attribute::NAME("vespa.mutate.on_second_phase.attribute");
    const std::string Attribute::DEFAULT_VALUE("");
    const std::string Operation::NAME("vespa.mutate.on_second_phase.operation");
    const std::string Operation::DEFAULT_VALUE("");

    std::string
    Attribute::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

    std::string
    Operation::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

}

namespace on_summary {

    const std::string Attribute::NAME("vespa.mutate.on_summary.attribute");
    const std::string Attribute::DEFAULT_VALUE("");
    const std::string Operation::NAME("vespa.mutate.on_summary.operation");
    const std::string Operation::DEFAULT_VALUE("");

    std::string
    Attribute::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

    std::string
    Operation::lookup(const Properties &props, const std::string &defaultValue) {
        return lookupString(props, NAME, defaultValue);
    }

}
} // namespace mutate

namespace feature_rename {
    const std::string Rename::NAME("vespa.feature.rename");

    std::vector<std::pair<std::string,std::string>>
    Rename::lookup(const Properties &props) {
        std::vector<std::pair<std::string,std::string>> retval;
        Property p = props.lookup(NAME);
        if (p.found()) {
            for (uint32_t i = 0; i+1 < p.size(); i += 2) {
                std::string from = p.getAt(i);
                std::string to = p.getAt(i+1);
                retval.emplace_back(from, to);
            }
        }
        return retval;
    }
} // namespace feature_rename

namespace match {

const std::string Feature::NAME("vespa.match.feature");
const std::vector<std::string> Feature::DEFAULT_VALUE;

std::vector<std::string>
Feature::lookup(const Properties &props)
{
    return lookupStringVector(props, NAME, DEFAULT_VALUE);
}

} // namespace match

namespace summary {

const std::string Feature::NAME("vespa.summary.feature");
const std::vector<std::string> Feature::DEFAULT_VALUE;

std::vector<std::string>
Feature::lookup(const Properties &props)
{
    return lookupStringVector(props, NAME, DEFAULT_VALUE);
}

} // namespace summary

namespace dump {

const std::string Feature::NAME("vespa.dump.feature");
const std::vector<std::string> Feature::DEFAULT_VALUE;

std::vector<std::string>
Feature::lookup(const Properties &props)
{
    return lookupStringVector(props, NAME, DEFAULT_VALUE);
}

const std::string IgnoreDefaultFeatures::NAME("vespa.dump.ignoredefaultfeatures");
const std::string IgnoreDefaultFeatures::DEFAULT_VALUE("false");

bool
IgnoreDefaultFeatures::check(const Properties &props)
{
    return checkIfTrue(props, NAME, DEFAULT_VALUE);
}

} // namespace dump

namespace matching {

const std::string TermwiseLimit::NAME("vespa.matching.termwise_limit");
const double TermwiseLimit::DEFAULT_VALUE(1.0);

double
TermwiseLimit::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

double
TermwiseLimit::lookup(const Properties &props, double defaultValue)
{
    return lookupDouble(props, NAME, defaultValue);
}

const std::string NumThreadsPerSearch::NAME("vespa.matching.numthreadspersearch");
const uint32_t NumThreadsPerSearch::DEFAULT_VALUE(std::numeric_limits<uint32_t>::max());

uint32_t
NumThreadsPerSearch::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

uint32_t
NumThreadsPerSearch::lookup(const Properties &props, uint32_t defaultValue)
{
    return lookupUint32(props, NAME, defaultValue);
}

const std::string NumSearchPartitions::NAME("vespa.matching.numsearchpartitions");
const uint32_t NumSearchPartitions::DEFAULT_VALUE(1);

uint32_t
NumSearchPartitions::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

uint32_t
NumSearchPartitions::lookup(const Properties &props, uint32_t defaultValue)
{
    return lookupUint32(props, NAME, defaultValue);
}

const std::string MinHitsPerThread::NAME("vespa.matching.minhitsperthread");
const uint32_t MinHitsPerThread::DEFAULT_VALUE(0);

uint32_t
MinHitsPerThread::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

uint32_t
MinHitsPerThread::lookup(const Properties &props, uint32_t defaultValue)
{
    return lookupUint32(props, NAME, defaultValue);
}

const std::string GlobalFilterLowerLimit::NAME("vespa.matching.global_filter.lower_limit");

const double GlobalFilterLowerLimit::DEFAULT_VALUE(0.05);

double
GlobalFilterLowerLimit::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

double
GlobalFilterLowerLimit::lookup(const Properties &props, double defaultValue)
{
    return lookupDouble(props, NAME, defaultValue);
}

const std::string GlobalFilterUpperLimit::NAME("vespa.matching.global_filter.upper_limit");

const double GlobalFilterUpperLimit::DEFAULT_VALUE(1.0);

double
GlobalFilterUpperLimit::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

double
GlobalFilterUpperLimit::lookup(const Properties &props, double defaultValue)
{
    return lookupDouble(props, NAME, defaultValue);
}

const std::string WeakAndStopWordAdjustLimit::NAME("vespa.matching.weakand.stop_word_adjust_limit");
const double WeakAndStopWordAdjustLimit::DEFAULT_VALUE(1.0);
double WeakAndStopWordAdjustLimit::lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
double WeakAndStopWordAdjustLimit::lookup(const Properties &props, double defaultValue) {
    return lookupDouble(props, NAME, defaultValue);
}

const std::string WeakAndStopWordDropLimit::NAME("vespa.matching.weakand.stop_word_drop_limit");
const double WeakAndStopWordDropLimit::DEFAULT_VALUE(1.0);
double WeakAndStopWordDropLimit::lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
double WeakAndStopWordDropLimit::lookup(const Properties &props, double defaultValue) {
    return lookupDouble(props, NAME, defaultValue);
}

const std::string DiskIndexBitvectorLimit::NAME("vespa.matching.diskindex.bitvector_limit");
const double DiskIndexBitvectorLimit::DEFAULT_VALUE(1.0);
double DiskIndexBitvectorLimit::lookup(const Properties& props) { return lookup(props, DEFAULT_VALUE); }
double DiskIndexBitvectorLimit::lookup(const Properties& props, double default_value) {
    return lookupDouble(props, NAME, default_value);
}

const std::string TargetHitsMaxAdjustmentFactor::NAME("vespa.matching.nns.target_hits_max_adjustment_factor");

const double TargetHitsMaxAdjustmentFactor::DEFAULT_VALUE(20.0);

double
TargetHitsMaxAdjustmentFactor::lookup(const Properties& props)
{
    return lookup(props, DEFAULT_VALUE);
}

double
TargetHitsMaxAdjustmentFactor::lookup(const Properties& props, double defaultValue)
{
    return lookupDouble(props, NAME, defaultValue);
}

const std::string FuzzyAlgorithm::NAME("vespa.matching.fuzzy.algorithm");
const vespalib::FuzzyMatchingAlgorithm FuzzyAlgorithm::DEFAULT_VALUE(vespalib::FuzzyMatchingAlgorithm::DfaTable);

vespalib::FuzzyMatchingAlgorithm
FuzzyAlgorithm::lookup(const Properties& props)
{
    return lookup(props, DEFAULT_VALUE);
}

vespalib::FuzzyMatchingAlgorithm
FuzzyAlgorithm::lookup(const Properties& props, vespalib::FuzzyMatchingAlgorithm default_value)
{
    auto value = lookupString(props, NAME, vespalib::to_string(default_value));
    return vespalib::fuzzy_matching_algorithm_from_string(value, default_value);
}

const std::string SortBlueprintsByCost::NAME("vespa.matching.sort_blueprints_by_cost");
const bool SortBlueprintsByCost::DEFAULT_VALUE(false);
bool SortBlueprintsByCost::check(const Properties &props, bool fallback) {
    return lookupBool(props, NAME, fallback);
}

const std::string AlwaysMarkPhraseExpensive::NAME("vespa.matching.always_mark_phrase_expensive");
const bool AlwaysMarkPhraseExpensive::DEFAULT_VALUE(false);
bool AlwaysMarkPhraseExpensive::check(const Properties &props, bool fallback) {
    return lookupBool(props, NAME, fallback);
}

} // namespace matching

namespace softtimeout {

const std::string Enabled::NAME("vespa.softtimeout.enable");
const bool Enabled::DEFAULT_VALUE(true);

bool Enabled::lookup(const Properties &props) {
    return lookupBool(props, NAME, DEFAULT_VALUE);
}

bool Enabled::lookup(const Properties &props, bool defaultValue) {
    return lookupBool(props, NAME, defaultValue);
}

const std::string TailCost::NAME("vespa.softtimeout.tailcost");
const double TailCost::DEFAULT_VALUE(0.1);

double TailCost::lookup(const Properties &props) {
    return lookupDouble(props, NAME, DEFAULT_VALUE);
}

const std::string Factor::NAME("vespa.softtimeout.factor");
const double Factor::DEFAULT_VALUE(0.5);

double Factor::lookup(const Properties &props) {
    return lookupDouble(props, NAME, DEFAULT_VALUE);
}
double Factor::lookup(const Properties &props, double defaultValue) {
    return lookupDouble(props, NAME, defaultValue);
}

bool Factor::isPresent(const Properties &props) {
    return props.lookup(NAME).found();
}

}

namespace matchphase {

const std::string DegradationAttribute::NAME("vespa.matchphase.degradation.attribute");
const std::string DegradationAttribute::DEFAULT_VALUE("");

const std::string DegradationAscendingOrder::NAME("vespa.matchphase.degradation.ascendingorder");
const bool DegradationAscendingOrder::DEFAULT_VALUE(false);

const std::string DegradationMaxHits::NAME("vespa.matchphase.degradation.maxhits");
const uint32_t DegradationMaxHits::DEFAULT_VALUE(0);

const std::string DegradationSamplePercentage::NAME("vespa.matchphase.degradation.samplepercentage");
const double DegradationSamplePercentage::DEFAULT_VALUE(0.2);

const std::string DegradationMaxFilterCoverage::NAME("vespa.matchphase.degradation.maxfiltercoverage");
const double DegradationMaxFilterCoverage::DEFAULT_VALUE(0.2);

const std::string DegradationPostFilterMultiplier::NAME("vespa.matchphase.degradation.postfiltermultiplier");
const double DegradationPostFilterMultiplier::DEFAULT_VALUE(1.0);

const std::string DiversityAttribute::NAME("vespa.matchphase.diversity.attribute");
const std::string DiversityAttribute::DEFAULT_VALUE("");

const std::string DiversityMinGroups::NAME("vespa.matchphase.diversity.mingroups");
const uint32_t DiversityMinGroups::DEFAULT_VALUE(1);

const std::string DiversityCutoffFactor::NAME("vespa.matchphase.diversity.cutoff.factor");
const double DiversityCutoffFactor::DEFAULT_VALUE(10.0);

const std::string DiversityCutoffStrategy::NAME("vespa.matchphase.diversity.cutoff.strategy");
const std::string DiversityCutoffStrategy::DEFAULT_VALUE("loose");

std::string
DegradationAttribute::lookup(const Properties &props, const std::string & defaultValue)
{
    return lookupString(props, NAME, defaultValue);
}

bool
DegradationAscendingOrder::lookup(const Properties &props, bool defaultValue)
{
    return lookupBool(props, NAME, defaultValue);
}

uint32_t
DegradationMaxHits::lookup(const Properties &props, uint32_t defaultValue)
{
    return lookupUint32(props, NAME, defaultValue);
}

double
DegradationSamplePercentage::lookup(const Properties &props, double defaultValue)
{
    return lookupDouble(props, NAME, defaultValue);
}

double
DegradationMaxFilterCoverage::lookup(const Properties &props, double defaultValue)
{
    return lookupDouble(props, NAME, defaultValue);
}

double
DegradationPostFilterMultiplier::lookup(const Properties &props, double defaultValue)
{
    return lookupDouble(props, NAME, defaultValue);
}

std::string
DiversityAttribute::lookup(const Properties &props, const std::string & defaultValue)
{
    return lookupString(props, NAME, defaultValue);
}

uint32_t
DiversityMinGroups::lookup(const Properties &props, uint32_t defaultValue)
{
    return lookupUint32(props, NAME, defaultValue);
}

double
DiversityCutoffFactor::lookup(const Properties &props, double defaultValue)
{
    return lookupDouble(props, NAME, defaultValue);
}

std::string
DiversityCutoffStrategy::lookup(const Properties &props, const std::string & defaultValue)
{
    return lookupString(props, NAME, defaultValue);
}

}

namespace trace {

const std::string Level::NAME("tracelevel");
const uint32_t Level::DEFAULT_VALUE(0);

uint32_t
Level::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

uint32_t
Level::lookup(const Properties &props, uint32_t defaultValue)
{
    return lookupUint32(props, NAME, defaultValue);
}

}

namespace hitcollector {

const std::string HeapSize::NAME("vespa.hitcollector.heapsize");
const uint32_t HeapSize::DEFAULT_VALUE(100);

uint32_t
HeapSize::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

uint32_t
HeapSize::lookup(const Properties &props, uint32_t defaultValue)
{
    return lookupUint32(props, NAME, defaultValue);
}

const std::string ArraySize::NAME("vespa.hitcollector.arraysize");
const uint32_t ArraySize::DEFAULT_VALUE(10000);

uint32_t
ArraySize::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

uint32_t
ArraySize::lookup(const Properties &props, uint32_t defaultValue)
{
    return lookupUint32(props, NAME, defaultValue);
}

const std::string EstimatePoint::NAME("vespa.hitcollector.estimatepoint");
const uint32_t EstimatePoint::DEFAULT_VALUE(0xffffffff);

uint32_t
EstimatePoint::lookup(const Properties &props)
{
    return lookupUint32(props, NAME, DEFAULT_VALUE);
}

const std::string EstimateLimit::NAME("vespa.hitcollector.estimatelimit");
const uint32_t EstimateLimit::DEFAULT_VALUE(0xffffffff);

uint32_t
EstimateLimit::lookup(const Properties &props)
{
    return lookupUint32(props, NAME, DEFAULT_VALUE);
}

const std::string FirstPhaseRankScoreDropLimit::NAME("vespa.hitcollector.rankscoredroplimit");
const std::optional<feature_t> FirstPhaseRankScoreDropLimit::DEFAULT_VALUE(std::nullopt);

std::optional<feature_t>
FirstPhaseRankScoreDropLimit::lookup(const Properties &props)
{
    return lookup(props, DEFAULT_VALUE);
}

std::optional<feature_t>
FirstPhaseRankScoreDropLimit::lookup(const Properties &props, std::optional<feature_t> default_value)
{
    return lookup_opt_double(props, NAME, default_value);
}

const std::string SecondPhaseRankScoreDropLimit::NAME("vespa.hitcollector.secondphase.rankscoredroplimit");
const std::optional<feature_t> SecondPhaseRankScoreDropLimit::DEFAULT_VALUE(std::nullopt);

std::optional<feature_t>
SecondPhaseRankScoreDropLimit::lookup(const Properties &props)
{
    return lookup_opt_double(props, NAME, DEFAULT_VALUE);
}

std::optional<feature_t>
SecondPhaseRankScoreDropLimit::lookup(const Properties &props, std::optional<feature_t> default_value)
{
    return lookup_opt_double(props, NAME, default_value);
}

} // namspace hitcollector


const std::string FieldWeight::BASE_NAME("vespa.fieldweight.");
const uint32_t FieldWeight::DEFAULT_VALUE(100);

uint32_t
FieldWeight::lookup(const Properties &props, const std::string &fieldName)
{
    return lookupUint32(props, BASE_NAME + fieldName, DEFAULT_VALUE);
}


const std::string IsFilterField::BASE_NAME("vespa.isfilterfield.");
const std::string IsFilterField::DEFAULT_VALUE("false");

void
IsFilterField::set(Properties &props, const std::string &fieldName)
{
    props.add(BASE_NAME + fieldName, "true");
}

bool
IsFilterField::check(const Properties &props, const std::string &fieldName)
{
    return checkIfTrue(props, BASE_NAME + fieldName, DEFAULT_VALUE);
}


namespace type {

const std::string Attribute::BASE_NAME("vespa.type.attribute.");
const std::string Attribute::DEFAULT_VALUE("");

std::string
Attribute::lookup(const Properties &props, const std::string &attributeName)
{
    return lookupString(props, BASE_NAME + attributeName, DEFAULT_VALUE);
}

void
Attribute::set(Properties &props, const std::string &attributeName, const std::string &type)
{
    props.add(BASE_NAME + attributeName, type);
}

const std::string QueryFeature::BASE_NAME("vespa.type.query.");
const std::string QueryFeature::DEFAULT_VALUE("");

std::string
QueryFeature::lookup(const Properties &props, const std::string &queryFeatureName)
{
    return lookupString(props, BASE_NAME + queryFeatureName, DEFAULT_VALUE);
}

void
QueryFeature::set(Properties &props, const std::string &queryFeatureName, const std::string &type)
{
    props.add(BASE_NAME + queryFeatureName, type);
}

} // namespace type

}
