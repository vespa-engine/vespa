// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprintresolver.h"
#include "featureexecutor.h"
#include "properties.h"
#include "matchdata.h"
#include "matchdatalayout.h"
#include "feature_resolver.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/array.h>
#include <set>
#include <vector>

namespace search {
namespace fef {

/**
 * A rank program is able to lazily calculate a set of feature
 * values. In order to access (and thereby calculate) output features
 * you typically use the get_seeds function to resolve the predefined
 * set of output features. Each feature value will be wrapped in a
 * LazyValue object that can be realized for a specific docid. The
 * rank program also owns the MatchData used to store unpacked
 * term-field match information. Note that you need unpack any
 * relevant posting information into the MatchData object before
 * trying to resolve lazy values.
 **/
class RankProgram
{
private:
    RankProgram(const RankProgram &) = delete;
    RankProgram &operator=(const RankProgram &) = delete;

    using MappedValues = std::map<const NumberOrObject *, LazyValue>;
    using ValueSet = std::set<const NumberOrObject *>;

    BlueprintResolver::SP            _resolver;
    MatchData::UP                    _match_data;
    vespalib::Stash                  _hot_stash;
    vespalib::Stash                  _cold_stash;
    std::vector<FeatureExecutor *>   _executors;
    MappedValues                     _unboxed_seeds;
    ValueSet                         _is_const;

    bool check_const(const NumberOrObject *value) const { return (_is_const.count(value) == 1); }
    bool check_const(FeatureExecutor *executor, const std::vector<BlueprintResolver::FeatureRef> &inputs) const;
    void run_const(FeatureExecutor *executor);
    void unbox(BlueprintResolver::FeatureRef seed);
    FeatureResolver resolve(const BlueprintResolver::FeatureMap &features, bool unbox_seeds) const;

public:
    typedef std::unique_ptr<RankProgram> UP;

    /**
     * Create a new rank program backed by the given resolver.
     *
     * @param resolver description on how to set up executors
     **/
    RankProgram(BlueprintResolver::SP resolver);
    ~RankProgram();

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
     * Expose the MatchData used when creating search iterators as it
     * is where all iterators should unpack their match information.
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
};

} // namespace fef
} // namespace search
