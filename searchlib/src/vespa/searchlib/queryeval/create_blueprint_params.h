// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "vespa/searchlib/fef/indexproperties.h"
#include "vespa/searchlib/queryeval/wand/wand_parts.h"
#include "vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h"

namespace search::queryeval {

/**
 * Parameters from a rank profile and query that are available when creating blueprints.
 */
struct CreateBlueprintParams
{
    double global_filter_lower_limit;
    double global_filter_upper_limit;
    double target_hits_max_adjustment_factor;
    vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm;
    double weakand_range;
    queryeval::wand::StopWordStrategy weakand_stop_word_strategy;

    CreateBlueprintParams(double global_filter_lower_limit_in,
                          double global_filter_upper_limit_in,
                          double target_hits_max_adjustment_factor_in,
                          vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm_in,
                          double weakand_range_in,
                          queryeval::wand::StopWordStrategy weakand_stop_word_strategy_in)
        : global_filter_lower_limit(global_filter_lower_limit_in),
          global_filter_upper_limit(global_filter_upper_limit_in),
          target_hits_max_adjustment_factor(target_hits_max_adjustment_factor_in),
          fuzzy_matching_algorithm(fuzzy_matching_algorithm_in),
          weakand_range(weakand_range_in),
          weakand_stop_word_strategy(weakand_stop_word_strategy_in)
    {
    }

    CreateBlueprintParams()
        : CreateBlueprintParams(fef::indexproperties::matching::GlobalFilterLowerLimit::DEFAULT_VALUE,
                                fef::indexproperties::matching::GlobalFilterUpperLimit::DEFAULT_VALUE,
                                fef::indexproperties::matching::TargetHitsMaxAdjustmentFactor::DEFAULT_VALUE,
                                fef::indexproperties::matching::FuzzyAlgorithm::DEFAULT_VALUE,
                                fef::indexproperties::temporary::WeakAndRange::DEFAULT_VALUE,
                                queryeval::wand::StopWordStrategy::none())
    {
    }
};

}
