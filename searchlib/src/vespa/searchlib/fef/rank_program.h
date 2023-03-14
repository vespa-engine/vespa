// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprintresolver.h"
#include "featureexecutor.h"
#include "properties.h"
#include "matchdata.h"
#include "feature_resolver.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/stllike/hash_set.h>

namespace vespalib { class ExecutionProfiler; }

namespace search::fef {

class IQueryEnvironment;

/**
 * A rank program is able to lazily calculate a set of feature
 * values. In order to access (and thereby calculate) output features
 * you typically use the get_seeds function to resolve the predefined
 * set of output features. Each feature value will be wrapped in a
 * LazyValue object that can be realized for a specific docid. Note
 * that you need unpack any relevant posting information into the
 * MatchData object passed to the setup function before trying to
 * resolve lazy values.
 **/
class RankProgram
{
private:
    using MappedValues = std::map<const NumberOrObject *, LazyValue>;
    using ValueSet = vespalib::hash_set<const NumberOrObject *, vespalib::hash<const NumberOrObject *>,
                                        std::equal_to<>, vespalib::hashtable_base::and_modulator>;

    BlueprintResolver::SP            _resolver;
    vespalib::Stash                  _hot_stash;
    vespalib::Stash                  _cold_stash;
    std::vector<FeatureExecutor *>   _executors;
    MappedValues                     _unboxed_seeds;
    ValueSet                         _is_const;

    bool check_const(const NumberOrObject *value) const { return (_is_const.count(value) == 1); }
    bool check_const(FeatureExecutor *executor, const std::vector<BlueprintResolver::FeatureRef> &inputs) const;
    void run_const(FeatureExecutor *executor);
    void unbox(BlueprintResolver::FeatureRef seed, const MatchData &md);
    FeatureResolver resolve(const BlueprintResolver::FeatureMap &features, bool unbox_seeds) const;

public:
    using UP = std::unique_ptr<RankProgram>;
    RankProgram(const RankProgram &) = delete;
    RankProgram &operator=(const RankProgram &) = delete;

    /**
     * Create a new rank program backed by the given resolver.
     *
     * @param resolver description on how to set up executors
     **/
    RankProgram(BlueprintResolver::SP resolver);
    ~RankProgram();

    size_t num_executors() const { return _executors.size(); }
    const FeatureExecutor &get_executor(size_t i) const { return *_executors[i]; }

    /**
     * Set up this rank program by creating the needed feature
     * executors and wiring them together. This function will also
     * pre-calculate all constant features.
     **/
    void setup(const MatchData &md,
               const IQueryEnvironment &queryEnv,
               const Properties &featureOverrides = Properties(),
               vespalib::ExecutionProfiler *profiler = nullptr);

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

}
