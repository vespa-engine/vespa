// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "feature_type.h"
#include <vector>
#include <map>

namespace search::fef {

class BlueprintFactory;
class IIndexEnvironment;
class FeatureNameParser;
class Blueprint;

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
    using SP = std::shared_ptr<BlueprintResolver>;
    using BlueprintSP = std::shared_ptr<Blueprint>;
    using Warnings = std::vector<vespalib::string>;

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
        [[nodiscard]] bool valid() const { return (executor != undef); }
    };
    using FeatureMap = std::map<vespalib::string, FeatureRef>;

    /**
     * Thin blueprint wrapper with additional information about how
     * the executor created from the blueprint should be wired with
     * other executors.
     **/
    struct ExecutorSpec {
        BlueprintSP              blueprint;
        std::vector<FeatureRef>  inputs;
        std::vector<FeatureType> output_types;

        explicit ExecutorSpec(BlueprintSP blueprint_in) noexcept;
        ExecutorSpec(ExecutorSpec &&) noexcept;
        ExecutorSpec & operator =(ExecutorSpec &&) noexcept;
        ExecutorSpec(const ExecutorSpec &);
        ~ExecutorSpec();
    };
    using ExecutorSpecList = std::vector<ExecutorSpec>;

    /**
     * The maximum dependency depth. This value is defined to protect
     * against infinitely deep dependency graphs and exposed for
     * testing purposes. It should be set high enough to avoid
     * problems for 'sane' developers and low enough to avoid stack
     * overflow.
     **/
    static constexpr uint32_t MAX_DEP_DEPTH = 256;

    /**
     * The maximum size of back-traces. Longer back-traces will be
     * logged with skipped entries somewhere in the middle. Exposed
     * for testing purposes.
     **/
    static constexpr int MAX_TRACE_SIZE = 16;

private:
    const BlueprintFactory       &_factory;
    const IIndexEnvironment      &_indexEnv;
    std::vector<vespalib::string> _seeds;
    ExecutorSpecList              _executorSpecs;
    FeatureMap                    _featureMap;
    FeatureMap                    _seedMap;
    Warnings                      _warnings;

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

    // Describe a feature based on its name (intended for log messages)
    //
    // rankingExpression(foo@hash) -> function 'foo'
    // feature -> rank feature 'feature'
    static vespalib::string describe_feature(const vespalib::string &name);

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
    [[nodiscard]] const ExecutorSpecList &getExecutorSpecs() const { return _executorSpecs; }

    /**
     * Obtain the location of all named features known to this
     * resolver. This may be used to dump a list of feature name/value
     * pairs after all feature values have been computed. The seeds
     * are the keys in the returned map, and the feature locations are
     * the values.
     *
     * @return feature locations
     **/
    [[nodiscard]] const FeatureMap &getFeatureMap() const { return _featureMap; }

    /**
     * Obtain the location of all seeds used by this resolver. This
     * may be used to dump a list of feature name/value pairs after
     * all feature values have been computed. The seeds are the keys
     * in the returned map, and the feature locations are the
     * values.
     *
     * @return seed locations
     **/
    [[nodiscard]] const FeatureMap &getSeedMap() const { return _seedMap; }

    /**
     * Will return any accumulated warnings during compile
     * @return list of warnings
     **/
    [[nodiscard]] const Warnings & getWarnings() const { return _warnings; }
};

}
