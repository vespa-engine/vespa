// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <queue>
#include <map>
#include "blueprint.h"
#include "feature_type.h"

namespace search::fef {

class BlueprintFactory;
class IIndexEnvironment;
class FeatureNameParser;

/**
 * This class is used by the framework to resolve blueprint
 * dependencies. A blueprint factory is used to create new blueprints
 * when needed during dependency resolving. Note that this class is
 * not inteded for direct use. It is used by the @ref RankSetup
 * class. It may also be used for low-level testing.
 **/
class BlueprintResolver
{
public:
    typedef std::shared_ptr<BlueprintResolver> SP;

    /**
     * Low-level reference to a single output from a feature
     * executor. 'executor' is the offset into the topological
     * ordering of all executors. This order is defined by the return
     * value from the getExecutorSpecs function. 'output' is the
     * offset into the ordered list of outputs from the relevant
     * executor.
     **/
    struct FeatureRef {
        uint32_t executor;
        uint32_t output;
        static constexpr uint32_t undef = -1;

        FeatureRef() : executor(undef), output(0) {}
        FeatureRef(uint32_t executor_in, uint32_t output_in)
            : executor(executor_in), output(output_in) {}
        bool valid() { return (executor != undef); }
    };
    typedef std::map<vespalib::string, FeatureRef> FeatureMap;

    /**
     * Thin blueprint wrapper with additional information about how
     * the executor created from the blueprint should be wired with
     * other executors.
     **/
    struct ExecutorSpec {
        Blueprint::SP            blueprint;
        std::vector<FeatureRef>  inputs;
        std::vector<FeatureType> output_types;

        ExecutorSpec(Blueprint::SP blueprint_in);
        ~ExecutorSpec();
    };
    typedef std::vector<ExecutorSpec> ExecutorSpecList;

    /**
     * The maximum dependency depth. This value is defined to protect
     * against infinitely deep dependency graphs and exposed for
     * testing purposes. It should be set high enough to avoid
     * problems for 'sane' developers and low enough to avoid stack
     * overflow.
     **/
    static const uint32_t MAX_DEP_DEPTH = 64;

private:
    const BlueprintFactory       &_factory;
    const IIndexEnvironment      &_indexEnv;
    std::vector<vespalib::string> _seeds;
    ExecutorSpecList              _executorSpecs;
    FeatureMap                    _featureMap;
    FeatureMap                    _seedMap;

public:
    BlueprintResolver(const BlueprintResolver &) = delete;
    BlueprintResolver &operator=(const BlueprintResolver &) = delete;
    ~BlueprintResolver();

    /**
     * Create a new blueprint resolver within the given index
     * environment and backed by the given factory.
     *
     * @param factory blueprint factory
     * @param indexEnv index environment
     **/
    BlueprintResolver(const BlueprintFactory &factory,
                      const IIndexEnvironment &indexEnv);

    /**
     * Add a feature name to the list of seeds. During compilation,
     * blueprints for all seeds and dependencies will be instantiated
     * and enumerated.
     *
     * @param feature feature name to use as a seed
     **/
    void addSeed(vespalib::stringref feature);

    /**
     * Create Blueprints for all seeds and dependencies and enumerate
     * blueprints in such a way that blueprints only depend on other
     * blueprints with lower enum values. Compilation will typically
     * fail if a dependency cannot be created or if you have circular
     * dependencies.
     *
     * @return true if ok, false if compilation error
     **/
    bool compile();

    /**
     * Obtain a vector indicating the order of instantiation of
     * feature executors and also how they should be wired together.
     * The enum value of an executor spec may be used directly as an
     * index into the returned vector.
     *
     * @return feature executor assembly directions
     **/
    const ExecutorSpecList &getExecutorSpecs() const;

    /**
     * Obtain the location of all named features known to this
     * resolver. This may be used to dump a list of feature name/value
     * pairs after all feature values have been computed. The seeds
     * are the keys in the returned map, and the feature locations are
     * the values.
     *
     * @return feature locations
     **/
    const FeatureMap &getFeatureMap() const;

    /**
     * Obtain the location of all seeds used by this resolver. This
     * may be used to dump a list of feature name/value pairs after
     * all feature values have been computed. The seeds are the keys
     * in the returned map, and the feature locations are the
     * values.
     *
     * @return seed locations
     **/
    const FeatureMap &getSeedMap() const;
};

}
