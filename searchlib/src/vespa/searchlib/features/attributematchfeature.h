// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"
#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

struct AttributeMatchParams {
    AttributeMatchParams() :
        attrInfo(nullptr), attribute(nullptr), weightedSet(false), maxWeight(256), fieldCompletenessImportance(0.05f) {}
    const fef::FieldInfo * attrInfo;
    const attribute::IAttributeVector * attribute;
    bool weightedSet;
    // config values
    int32_t maxWeight;
    feature_t fieldCompletenessImportance;
};

/**
 * Implements the executor for the attribute match feature.
 */
template <typename T>
class AttributeMatchExecutor : public fef::FeatureExecutor {
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
        int32_t   _maxWeight;
        feature_t _normalizedWeightedWeight;
        int32_t   _weightSum; // sum of the weights for a weighted set attribute
        uint32_t  _valueCount; // the number of values for a non-weighted set attribute
        const fef::MatchData *_md;

    public:
        Computer(const fef::IQueryEnvironment & env, AttributeMatchParams params);
        void run(uint32_t docId);
        void reset();
        uint32_t getNumTerms() const { return _queryTerms.size(); }
        uint32_t getMatches() const { return _matches; }
        int32_t getTotalWeight() const { return _totalWeight; }
        int32_t getMaxWeight() const { return _maxWeight; }
        feature_t getAverageWeight() const;
        feature_t getQueryCompleteness() const;
        feature_t getNormalizedWeight() const;
        feature_t getNormalizedWeightedWeight() const;
        feature_t getFieldCompleteness() const;
        feature_t getCompleteness() const;
        feature_t getWeight() const;
        feature_t getSignificance() const;
        feature_t getImportance() const { return (getWeight() + getSignificance()) * 0.5; }
        void bind_match_data(const fef::MatchData &md) { _md = &md; }
    };

    Computer _cmp;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    /**
     * Constructs an executor.
     */
    AttributeMatchExecutor(const fef::IQueryEnvironment & env, AttributeMatchParams params);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the attribute match executor.
 */
class AttributeMatchBlueprint : public fef::Blueprint {
private:
    AttributeMatchParams _params;

public:
    AttributeMatchBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().attributeField(fef::ParameterDataTypeSet::normalTypeSet(), fef::ParameterCollection::ANY);
    }

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;

    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
