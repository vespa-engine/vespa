// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprintresolver.h"
#include "featureexecutor.h"
#include "properties.h"
#include "matchdata.h"
#include "matchdatalayout.h"
#include "feature_resolver.h"
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory.h>
#include <vespa/vespalib/util/array.h>

namespace search {
namespace fef {

/**
 * A rank program runs multiple feature executors in a predefined
 * order to produce a set of feature values. The rank program owns the
 * MatchData used to store unpacked term-field match information and
 * feature values used during evaluation.
 **/
class RankProgram
{
private:
    RankProgram(const RankProgram &) = delete;
    RankProgram &operator=(const RankProgram &) = delete;

    using MappedValues = std::map<const NumberOrObject *, const NumberOrObject *>;

    BlueprintResolver::SP                 _resolver;
    MatchData::UP                         _match_data;
    vespalib::Stash                       _hot_stash;
    vespalib::Stash                       _cold_stash;
    vespalib::ArrayRef<FeatureExecutor *> _program;
    std::vector<FeatureExecutor *>        _executors;
    MappedValues                          _unboxed_seeds;

public:
    typedef std::unique_ptr<RankProgram> UP;

    /**
     * Create a new rank program backed by the given resolver.
     *
     * @param resolver description on how to set up executors
     **/
    RankProgram(BlueprintResolver::SP resolver);

    size_t program_size() const { return _program.size(); }
    size_t num_executors() const { return _executors.size(); }

    /**
     * Set up this rank program by creating the needed feature
     * executors and wiring them together. This function will also
     * create the MatchData to be used for iterator unpacking as well
     * as pre-calculating all constant features.
     **/
    void setup(const MatchDataLayout &mdl,
               const IQueryEnvironment &queryEnv,
               const Properties &featureOverrides = Properties());

    /**
     * Expose the MatchData containing all calculated features. This
     * is also used when creating search iterators as it is where all
     * iterators should unpack their match information.
     **/
    MatchData &match_data() { return *_match_data; }
    const MatchData &match_data() const { return *_match_data; }

    /**
     * Obtain the names and storage locations of all seed features for
     * this rank program. Programs for ranking phases will only have a
     * single seed while programs used for summary features or
     * scraping will have multiple seeds.
     *
     * @params unbox_seeds make sure seeds values are numbers
     **/
    FeatureResolver get_seeds(bool unbox_seeds = true) const;

    /**
     * Obtain the names and storage locations of all features for this
     * rank program. This method is intended for debugging and
     * testing.
     *
     * @params unbox_seeds make sure seeds values are numbers
     **/
    FeatureResolver get_all_features(bool unbox_seeds = true) const;

    /**
     * Run this rank program on the current state of the internal
     * match data for the given docid. Typically, match data for a
     * specific result will be unpacked before calling run. After run
     * is called, the wanted results can be extracted using the
     * appropriate feature handles. The given docid will be used to
     * tag the internal match data container before execution. Match
     * data for individual term/field combinations are only considered
     * valid if their docid matches that of the match data container.
     *
     * @param docid the document we are ranking
     **/
    void run(uint32_t docid) {
        for (FeatureExecutor *executor: _program) {
            executor->execute(docid);
        }
    }
};

} // namespace fef
} // namespace search
