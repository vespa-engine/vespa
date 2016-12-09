// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/common/feature.h>
#include "queryterm.h"

namespace search {
namespace features {

struct AttributeMatchParams {
    AttributeMatchParams() :
        attrInfo(NULL), attribute(NULL), weightedSet(false), maxWeight(256), fieldCompletenessImportance(0.05f) {}
    const search::fef::FieldInfo * attrInfo;
    const search::attribute::IAttributeVector * attribute;
    bool weightedSet;
    // config values
    int32_t maxWeight;
    feature_t fieldCompletenessImportance;
};

/**
 * Implements the executor for the attribute match feature.
 */
template <typename T>
class AttributeMatchExecutor : public search::fef::FeatureExecutor {
private:
    /**
     * This class is used to compute metrics for match in an attribute vector.
     */
    class Computer {
    private:
        // TermData pointer and significance
        AttributeMatchParams _params;
        mutable T _buffer; // used when fetching weights from a weighted set attribute

        // per query
        uint32_t  _numAttrTerms;
        uint32_t  _totalTermWeight;       // total weight of all terms
        feature_t _totalTermSignificance; // total significance of all terms
        uint32_t  _totalAttrTermWeight;   // weight of all attribute terms
        QueryTermVector _queryTerms; // the terms searching this attribute

        // per doc
        uint32_t  _matches;
        uint32_t  _matchedTermWeight;       // term weight of matched terms
        feature_t _matchedTermSignificance; // significance of matched terms
        int32_t   _totalWeight;
        feature_t _normalizedWeightedWeight;
        int32_t   _weightSum; // sum of the weights for a weighted set attribute
        uint32_t  _valueCount; // the number of values for a non-weighted set attribute
        const fef::MatchData *_md;

    public:
        Computer(const search::fef::IQueryEnvironment & env,
                 AttributeMatchParams params);
        void run(search::fef::MatchData & data);
        void reset();
        uint32_t getNumTerms() const { return _queryTerms.size(); }
        uint32_t getMatches() const { return _matches; }
        int32_t getTotalWeight() const { return _totalWeight; }
        feature_t getAverageWeight() const;
        feature_t getQueryCompleteness() const;
        feature_t getNormalizedWeight() const;
        feature_t getNormalizedWeightedWeight() const;
        feature_t getFieldCompleteness() const;
        feature_t getCompleteness() const;
        feature_t getWeight() const;
        feature_t getSignificance() const;
        feature_t getImportance() const { return (getWeight() + getSignificance()) * 0.5; }
        void bind_match_data(fef::MatchData &md) { _md = &md; }
    };

    Computer _cmp;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    /**
     * Constructs an executor.
     */
    AttributeMatchExecutor(const search::fef::IQueryEnvironment & env,
                           AttributeMatchParams params);

    // Inherit doc from FeatureExecutor.
    virtual void execute(search::fef::MatchData & data);
};


/**
 * Implements the blueprint for the attribute match executor.
 */
class AttributeMatchBlueprint : public search::fef::Blueprint {
private:
    AttributeMatchParams _params;

public:
    /**
     * Constructs a blueprint.
     */
    AttributeMatchBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().attributeField(search::fef::ParameterCollection::ANY);
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

