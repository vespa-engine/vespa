// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <vespa/searchlib/common/feature.h>

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
    static const vespalib::string NAME;
    static bool check(const Properties &props, bool default_value);
};

// use fast-forest evaluation for gbdt expressions. affects rank/summary/dump
struct UseFastForest {
    static const vespalib::string NAME;
    static const bool DEFAULT_VALUE;
    static bool check(const Properties &props);
};

} // namespace eval

namespace rank {

    /**
     * Property for the feature name used for first phase rank.
     **/
    struct FirstPhase {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props);
    };

    /**
     * Property for the feature name used for second phase rank.
     **/
    struct SecondPhase {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props);
    };

} // namespace rank

namespace feature_rename {

    /**
     * Property for match/summary/dump featuress that should be exposed
     * with a different name, typically rankingExpression(foo) -> foo
     **/
    struct Rename {
        static const vespalib::string NAME;
        static std::vector<std::pair<vespalib::string,vespalib::string>> lookup(const Properties &props);
    };


} // namespace featurerename

namespace match {

    /**
     * Property for the set of features to be inserted into the search
     * reply (match features).
     **/
    struct Feature {
        static const vespalib::string NAME;
        static const std::vector<vespalib::string> DEFAULT_VALUE;
        static std::vector<vespalib::string> lookup(const Properties &props);
    };

} // namespace match

namespace summary {

    /**
     * Property for the set of features to be inserted into the
     * summaryfeatures docsum field
     **/
    struct Feature {
        static const vespalib::string NAME;
        static const std::vector<vespalib::string> DEFAULT_VALUE;
        static std::vector<vespalib::string> lookup(const Properties &props);
    };

} // namespace summary

namespace dump {

    /**
     * Property for the set of feature names used for dumping.
     **/
    struct Feature {
        static const vespalib::string NAME;
        static const std::vector<vespalib::string> DEFAULT_VALUE;
        static std::vector<vespalib::string> lookup(const Properties &props);
    };

    /**
     * Property that may be used to ignore default rank features when
     * dumping.
     **/
    struct IgnoreDefaultFeatures {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static bool check(const Properties &props);
    };

} // namespace dump

namespace execute::onmatch {
    struct Attribute {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
    struct Operation {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
}

namespace execute::onrerank {
    struct Attribute {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
    struct Operation {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
}

namespace execute::onsummary {
    struct Attribute {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
    struct Operation {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
}

namespace mutate {
    //TODO Remove October 2022
    struct AllowQueryOverride {
        static const vespalib::string NAME;
        static bool check(const Properties &props);
    };
}

namespace mutate::on_match {
    struct Attribute {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
    struct Operation {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
}

namespace mutate::on_first_phase {
    struct Attribute {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
    struct Operation {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
}

namespace mutate::on_second_phase {
    struct Attribute {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
    struct Operation {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
}

namespace mutate::on_summary {
    struct Attribute {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };
    struct Operation {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
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
        static const vespalib::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Property for the number of threads used per search.
     **/
    struct NumThreadsPerSearch {
        static const vespalib::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };
    /**
     * Property for the minimum number of hits per thread.
     **/
    struct MinHitsPerThread {
        static const vespalib::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };
    /**
     * Property for the number of partitions inside the docid space.
     * A partition is a unit of work for the search threads.
     **/
    struct NumSearchPartitions {
        static const vespalib::string NAME;
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
        static const vespalib::string NAME;
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
        static const vespalib::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
        static double lookup(const Properties &props, double defaultValue);
    };
}

namespace softtimeout {
    /**
     * Enables or disables the soft timeout.
     * Default is off, but will change in Q1 2017
     */
    struct Enabled {
        static const vespalib::string NAME;
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
        static const vespalib::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props);
    };

    /**
     * This can be controlled in the query to override the factor that the backend maintains.
     * The backend starts off with a value of 0.5.
     */
    struct Factor {
        static const vespalib::string NAME;
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
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };

    /**
     * Property for the order used for graceful degradation during match phase.
     **/
    struct DegradationAscendingOrder {
        static const vespalib::string NAME;
        static const bool DEFAULT_VALUE;
        static bool lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static bool lookup(const Properties &props, bool defaultValue);
    };

    /**
     * Property for how many hits the used wanted for graceful degradation during match phase.
     **/
    struct DegradationMaxHits {
        static const vespalib::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    /**
     * Property for how many hits out of wanted hits to collect before considering graceful degradation during match phase.
     **/
    struct DegradationSamplePercentage {
        static const vespalib::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static double lookup(const Properties &props, double defaultValue);
    };

    struct DegradationMaxFilterCoverage {
        static const vespalib::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static double lookup(const Properties &props, double defaultValue);
    };

    /**
     * Property for moving the swithpoint between pre and post filtering.
     * > 1 favors pre filtering, less favour post filtering
     **/
    struct DegradationPostFilterMultiplier {
        static const vespalib::string NAME;
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
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };

    /**
     * If we were to later group on the diversity attribute, try not
     * to end up with fewer groups than this number. If this property
     * is 1 (the default) diversity will be disabled.
     **/
    struct DiversityMinGroups {
        static const vespalib::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    struct DiversityCutoffFactor {
        static const vespalib::string NAME;
        static const double DEFAULT_VALUE;
        static double lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static double lookup(const Properties &props, double defaultValue);
    };
    struct DiversityCutoffStrategy {
        static const vespalib::string NAME;
        static const vespalib::string DEFAULT_VALUE;
        static vespalib::string lookup(const Properties &props) { return lookup(props, DEFAULT_VALUE); }
        static vespalib::string lookup(const Properties &props, const vespalib::string & defaultValue);
    };

} // namespace matchphase

namespace trace {

    /**
 * Property for the heap size used in the hit collector.
 **/
    struct Level {
        static const vespalib::string NAME;
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
        static const vespalib::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    /**
     * Property for the array size used in the hit collector.
     **/
    struct ArraySize {
        static const vespalib::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
        static uint32_t lookup(const Properties &props, uint32_t defaultValue);
    };

    /**
     * Property for the estimate point used in parallel query evaluation.
     * Specifies when to estimate the total number of hits.
     **/
    struct EstimatePoint {
        static const vespalib::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
    };

    /**
     * Property for the estimate limit used in parallel query evaluation.
     * Specifies the limit for a hit estimate. If the estimate is above the limit abort ranking.
     **/
    struct EstimateLimit {
        static const vespalib::string NAME;
        static const uint32_t DEFAULT_VALUE;
        static uint32_t lookup(const Properties &props);
    };

    /**
     * Property for the rank score drop limit used in parallel query evaluation.
     * Drop a hit if the rank score <= drop limit.
     **/
    struct RankScoreDropLimit {
        static const vespalib::string NAME;
        static const feature_t DEFAULT_VALUE;
        static feature_t lookup(const Properties &props);
        static feature_t lookup(const Properties &props, feature_t defaultValue);
    };


} // namespace hitcollector

/**
 * Property for the field weight of a field.
 **/
struct FieldWeight {
    static const vespalib::string BASE_NAME;
    static const uint32_t DEFAULT_VALUE;
    static uint32_t lookup(const Properties &props, const vespalib::string &fieldName);
};

/**
 * Property for whether a field is a filter field.
 **/
struct IsFilterField {
    static const vespalib::string BASE_NAME;
    static const vespalib::string DEFAULT_VALUE;
    static void set(Properties &props, const vespalib::string &fieldName);
    static bool check(const Properties &props, const vespalib::string &fieldName);
};

namespace type {

/**
 * Property for the type of an attribute.
 * Currently, only tensor types are specified using this.
 */
struct Attribute {
    static const vespalib::string BASE_NAME;
    static const vespalib::string DEFAULT_VALUE;
    static vespalib::string lookup(const Properties &props, const vespalib::string &attributeName);
    static void set(Properties &props, const vespalib::string &attributeName, const vespalib::string &type);
};

/**
 * Property for the type of a query feature.
 * Currently, only tensor types are specified using this.
 */
struct QueryFeature {
    static const vespalib::string BASE_NAME;
    static const vespalib::string DEFAULT_VALUE;
    static vespalib::string lookup(const Properties &props, const vespalib::string &queryFeatureName);
    static void set(Properties &props, const vespalib::string &queryFeatureName, const vespalib::string &type);
};

} // namespace type

}
