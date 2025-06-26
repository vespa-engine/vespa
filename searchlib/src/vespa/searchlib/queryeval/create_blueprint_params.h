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
    double acorn_one_upper_limit;
    double acorn_one_exploration;
    double target_hits_max_adjustment_factor;
    vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm;
    queryeval::wand::StopWordStrategy weakand_stop_word_strategy;
    std::optional<double> filter_threshold;

    CreateBlueprintParams(double global_filter_lower_limit_in,
                          double global_filter_upper_limit_in,
                          double acorn_one_upper_limit_in,
                          double acorn_one_exploration_in,
                          double target_hits_max_adjustment_factor_in,
                          vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm_in,
                          queryeval::wand::StopWordStrategy weakand_stop_word_strategy_in,
                          std::optional<double> filter_threshold_in)
        : global_filter_lower_limit(global_filter_lower_limit_in),
          global_filter_upper_limit(global_filter_upper_limit_in),
          acorn_one_upper_limit(acorn_one_upper_limit_in),
          acorn_one_exploration(acorn_one_exploration_in),
          target_hits_max_adjustment_factor(target_hits_max_adjustment_factor_in),
          fuzzy_matching_algorithm(fuzzy_matching_algorithm_in),
          weakand_stop_word_strategy(weakand_stop_word_strategy_in),
          filter_threshold(filter_threshold_in)
    {
    }

    CreateBlueprintParams()
        : CreateBlueprintParams(fef::indexproperties::matching::GlobalFilterLowerLimit::DEFAULT_VALUE,
                                fef::indexproperties::matching::GlobalFilterUpperLimit::DEFAULT_VALUE,
                                fef::indexproperties::matching::AcornOneUpperLimit::DEFAULT_VALUE,
                                fef::indexproperties::matching::AcornOneExploration::DEFAULT_VALUE,
                                fef::indexproperties::matching::TargetHitsMaxAdjustmentFactor::DEFAULT_VALUE,
                                fef::indexproperties::matching::FuzzyAlgorithm::DEFAULT_VALUE,
                                queryeval::wand::StopWordStrategy::none(),
                                std::nullopt)
    {
    }
};

}
