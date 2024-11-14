// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace search::fef { class Properties; }

/**
 * This namespace is a placeholder for several structs, each representing
 * an index property with name and default value. All property names
 * defined here will have the prefix "vespa." and are known by the
 * feature execution framework. When accessing an index property from a @ref Properties
 * instance one should use the property names defined here to perform the lookup.
 * If the property is not present the default value is used.
 **/
namespace search::fef::indexproperties {

namespace eval {

// lazy evaluation of expressions. affects rank/summary/dump
struct LazyExpressions {
    static const std::string NAME;
    static bool check(const Properties &props, bool default_value);
};

// use fast-forest evaluation for gbdt expressions. affects rank/summary/dump
struct UseFastForest {
    static const std::string NAME;
    static const bool DEFAULT_VALUE;
    static bool check(const Properties &props);
};

} // namespace eval

namespace rank {

    /**
     * Property for the feature name used for first phase rank.
     **/
    struct FirstPhase {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props);
    };

    /**
     * Property for the feature name used for second phase rank.
     **/
    struct SecondPhase {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props);
    };

} // namespace rank

namespace feature_rename {

    /**
     * Property for match/summary/dump featuress that should be exposed
     * with a different name, typically rankingExpression(foo) -> foo
     **/
    struct Rename {
        static const std::string NAME;
        static std::vector<std::pair<std::string,std::string>> lookup(const Properties &props);
    };


} // namespace featurerename

namespace match {

    /**
     * Property for the set of features to be inserted into the search
     * reply (match features).
     **/
    struct Feature {
        static const std::string NAME;
        static const std::vector<std::string> DEFAULT_VALUE;
        static std::vector<std::string> lookup(const Properties &props);
    };

} // namespace match

namespace summary {

    /**
     * Property for the set of features to be inserted into the
     * summaryfeatures docsum field
     **/
    struct Feature {
        static const std::string NAME;
        static const std::vector<std::string> DEFAULT_VALUE;
        static std::vector<std::string> lookup(const Properties &props);
    };

} // namespace summary

namespace dump {

    /**
     * Property for the set of feature names used for dumping.
     **/
    struct Feature {
        static const std::string NAME;
        static const std::vector<std::string> DEFAULT_VALUE;
        static std::vector<std::string> lookup(const Properties &props);
    };

    /**
     * Property that may be used to ignore default rank features when
     * dumping.
     **/
    struct IgnoreDefaultFeatures {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static bool check(const Properties &props);
    };

} // namespace dump

namespace execute::onmatch {
    struct Attribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
    struct Operation {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
}

namespace execute::onrerank {
    struct Attribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
    struct Operation {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
}

namespace execute::onsummary {
    struct Attribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
    struct Operation {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
}

namespace mutate {
    //TODO Remove October 2022
    struct AllowQueryOverride {
        static const std::string NAME;
        static bool check(const Properties &props);
    };
}

// Add temporary flags used for safe rollout of new features here
namespace temporary {
/**
 * A number in the range [0,1] for the effective idf range for WeakAndOperator.
 * 1.0 will give the complete range as used by default by bm25.
 * scaled_idf = (1.0 - range) * max_idf + (range * idf)
 * 0.0 which is default gives default legacy behavior.
 **/
struct WeakAndRange {
    static const std::string NAME;
    static const double DEFAULT_VALUE;
    static double lookup(const Properties &props);
    static double lookup(const Properties &props, double defaultValue);
};
}

namespace mutate::on_match {
    struct Attribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
    struct Operation {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
}

namespace mutate::on_first_phase {
    struct Attribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
    struct Operation {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
}

namespace mutate::on_second_phase {
    struct Attribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
    struct Operation {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
}

namespace mutate::on_summary {
    struct Attribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
    struct Operation {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };
}

namespace matching {

    /**
     * A number in the range [0,1] indicating how much of the corpus
     * the query must match for termwise evaluation to be enabled. 1
     * means never allowed. 0 means always allowed. The default value
     * is 1 (never).
     **/
    struct TermwiseLimit {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Property for the number of threads used per search.
     **/
    struct NumThreadsPerSearch {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };
    /**
     * Property for the minimum number of hits per thread.
     **/
    struct MinHitsPerThread {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };
    /**
     * Property for the number of partitions inside the docid space.
     * A partition is a unit of work for the search threads.
     **/
    struct NumSearchPartitions {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    /**
     * Property to control fallback to not building a global filter
     * for a query with a blueprint that wants a global filter. If the
     * estimated ratio of matching documents is less than this limit
     * then don't build a global filter. The effect will be falling back to bruteforce instead of approximation.
     **/
    struct GlobalFilterLowerLimit {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Property to control not building a global filter
     * for a query with a blueprint that wants a global filter. If the
     * estimated ratio of matching documents is larger than this limit
     * then don't build a global filter, but assumes that the expected filter ratio has been
     * taken care of increasing recall. Increasing recall by 1/upper_limit * 1.2 is probably a sane solution
     * adding 20% margin to handle some correlation between filter and rest of query.
     **/
    struct GlobalFilterUpperLimit {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Property to control the auto-adjustment of targetHits in a nearestNeighbor search using HNSW index with post-filtering.
     *
     * The targetHits is auto-adjusted in an effort to expose targetHits hits to first-phase ranking after post-filtering:
     * adjustedTargetHits = min(targetHits / estimatedHitRatio, targetHits * targetHitsMaxAdjustmentFactor).
     *
     * This property ensures an upper bound of adjustedTargetHits, avoiding that the search in the HNSW index takes too long.
     **/
    struct TargetHitsMaxAdjustmentFactor {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Try to find a word matching less that this whose score will be used as initial heap threshold.
     * The value is given as a fraction of the corpus in the range [0,1]
     **/
    struct WeakAndStopWordAdjustLimit {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Terms matching more than this will be dropped from the query altogether.
     * The value is given as a fraction of the corpus in the range [0,1]
     **/
    struct WeakAndStopWordDropLimit {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Property to control the algorithm using for fuzzy matching.
     **/
    struct FuzzyAlgorithm {
        static const std::string NAME;
        static const vespalib::FuzzyMatchingAlgorithm DEFAULT_VALUE;
        static vespalib::FuzzyMatchingAlgorithm lookup(const Properties& props);
        static vespalib::FuzzyMatchingAlgorithm lookup(const Properties& props, vespalib::FuzzyMatchingAlgorithm default_value);
    };

    /**
     * Sort blueprints based on relative cost estimate rather than est_hits
     **/
    struct SortBlueprintsByCost {
        static const std::string NAME;
        static const bool DEFAULT_VALUE;
        static bool check(const Properties &props) { return check(props, DEFAULT_VALUE); }
        static bool check(const Properties &props, bool fallback);
    };

    /**
     * When enabled, the unpacking part of the phrase iterator will be tagged as expensive
     * under all intermediate iterators, not only AND.
     **/
    struct AlwaysMarkPhraseExpensive {
        static const std::string NAME;
        static const bool DEFAULT_VALUE;
        static bool check(const Properties &props) { return check(props, DEFAULT_VALUE); }
        static bool check(const Properties &props, bool fallback);
    };
}

namespace softtimeout {
    /**
     * Enables or disables the soft timeout.
     * Default is off, but will change in Q1 2017
     */
    struct Enabled {
        static const std::string NAME;
        static const bool DEFAULT_VALUE;
        static bool lookup(const Properties &props);
        static bool lookup(const Properties &props, bool defaultValue);
    };
    /**
     * Specifies how large factor [0-1] of the given timeout that is
     * allocated to stuff after searchphase has completed.
     * Be it summary fetching or what not. default is 0.10 or 10%.
     */
    struct TailCost {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
    };

    /**
     * This can be controlled in the query to override the factor that the backend maintains.
     * The backend starts off with a value of 0.5.
     */
    struct Factor {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
        static bool isPresent(const Properties &props);
    };
}

namespace matchphase {

    /**
     * Property for the attribute used for graceful degradation during match phase.
     **/
    struct DegradationAttribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };

    /**
     * Property for the order used for graceful degradation during match phase.
     **/
    struct DegradationAscendingOrder {
        static const std::string NAME;
        static const bool DEFAULT_VALUE;
        static bool lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static bool lookup(const Properties &props, bool defaultValue);
    };

    /**
     * Property for how many hits the used wanted for graceful degradation during match phase.
     **/
    struct DegradationMaxHits {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    /**
     * Property for how many hits out of wanted hits to collect before considering graceful degradation during match phase.
     **/
    struct DegradationSamplePercentage {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static double lookup(const Properties &props, double defaultValue);
    };

    struct DegradationMaxFilterCoverage {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Property for moving the swithpoint between pre and post filtering.
     * > 1 favors pre filtering, less favour post filtering
     **/
    struct DegradationPostFilterMultiplier {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * The name of the attribute used to ensure result diversity
     * during match phase limiting. If this property is "" (empty
     * string; the default) diversity will be disabled.
     **/
    struct DiversityAttribute {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };

    /**
     * If we were to later group on the diversity attribute, try not
     * to end up with fewer groups than this number. If this property
     * is 1 (the default) diversity will be disabled.
     **/
    struct DiversityMinGroups {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    struct DiversityCutoffFactor {
        static const std::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static double lookup(const Properties &props, double defaultValue);
    };
    struct DiversityCutoffStrategy {
        static const std::string NAME;
        static const std::string DEFAULT_VALUE;
        static std::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static std::string lookup(const Properties &props, const std::string & defaultValue);
    };

} // namespace matchphase

namespace trace {

    /**
 * Property for the heap size used in the hit collector.
 **/
    struct Level {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

}


namespace hitcollector {

    /**
     * Property for the heap size used in the hit collector.
     **/
    struct HeapSize {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    /**
     * Property for the array size used in the hit collector.
     **/
    struct ArraySize {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    /**
     * Property for the estimate point used in parallel query evaluation.
     * Specifies when to estimate the total number of hits.
     **/
    struct EstimatePoint {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
    };

    /**
     * Property for the estimate limit used in parallel query evaluation.
     * Specifies the limit for a hit estimate. If the estimate is above the limit abort ranking.
     **/
    struct EstimateLimit {
        static const std::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
    };

    /**
     * Property for the first phase rank score drop limit used in parallel
     * query evaluation.
     * Drop a hit if the first phase rank score <= drop limit.
     **/
    struct FirstPhaseRankScoreDropLimit {
        static const std::string NAME;
        static const std::optional<feature_t> DEFAULT_VALUE;
        static std::optional<feature_t> lookup(const Properties &props);
        static std::optional<feature_t> lookup(const Properties &props, std::optional<feature_t> default_value);
    };

    /**
     * Property for the second phase rank score drop limit used in
     * parallel query evaluation.  Drop a hit if the score (reranked or
     * rescored) <= drop limit.
     **/
    struct SecondPhaseRankScoreDropLimit {
        static const std::string NAME;
        static const std::optional<feature_t> DEFAULT_VALUE;
        static std::optional<feature_t> lookup(const Properties &props);
        static std::optional<feature_t> lookup(const Properties &props, std::optional<double> default_value);
    };

} // namespace hitcollector

/**
 * Property for the field weight of a field.
 **/
struct FieldWeight {
    static const std::string BASE_NAME;
    static const uint32_t DEFAULT_VALUE;
    static uint32_t lookup(const Properties &props, const std::string &fieldName);
};

/**
 * Property for whether a field is a filter field.
 **/
struct IsFilterField {
    static const std::string BASE_NAME;
    static const std::string DEFAULT_VALUE;
    static void set(Properties &props, const std::string &fieldName);
    static bool check(const Properties &props, const std::string &fieldName);
};

namespace type {

/**
 * Property for the type of an attribute.
 * Currently, only tensor types are specified using this.
 */
struct Attribute {
    static const std::string BASE_NAME;
    static const std::string DEFAULT_VALUE;
    static std::string lookup(const Properties &props, const std::string &attributeName);
    static void set(Properties &props, const std::string &attributeName, const std::string &type);
};

/**
 * Property for the type of a query feature.
 * Currently, only tensor types are specified using this.
 */
struct QueryFeature {
    static const std::string BASE_NAME;
    static const std::string DEFAULT_VALUE;
    static std::string lookup(const Properties &props, const std::string &queryFeatureName);
    static void set(Properties &props, const std::string &queryFeatureName, const std::string &type);
};

} // namespace type

}
