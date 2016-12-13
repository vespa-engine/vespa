// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "handle.h"
#include "termfieldmatchdata.h"
#include <vespa/searchlib/common/feature.h>
#include <memory>
#include <vector>
#include <vespa/vespalib/eval/value.h>
#include "number_or_object.h"

namespace search {
namespace fef {

/**
 * An object of this class is used to store all basic data and derived
 * features for a single hit.
 **/
class MatchData
{
private:
    std::vector<TermFieldMatchData> _termFields;
    std::vector<NumberOrObject>     _features;
    std::vector<bool>               _feature_is_object;
    double                          _termwise_limit;

public:
    /**
     * Wrapper for constructor parameters
     **/
    class Params
    {
    private:
        uint32_t _numTermFields;
        uint32_t _numFeatures;

        friend class ::search::fef::MatchData;
        Params() : _numTermFields(0), _numFeatures(0) {}
    public:
        uint32_t numTermFields() const { return _numTermFields; }
        Params & numTermFields(uint32_t value) {
            _numTermFields = value;
            return *this;
        }

        uint32_t numFeatures() const { return _numFeatures; }
        Params & numFeatures(uint32_t value) {
            _numFeatures = value;
            return *this;
        }
    };
    /**
     * Avoid C++'s most vexing parse problem.
     * (reference: http://www.amazon.com/dp/0201749629/)
     **/
    static Params params() { return Params(); }

    /**
     * Convenience typedef for an auto-pointer to this class.
     **/
    typedef std::unique_ptr<MatchData> UP;

    /**
     * Create a new object with the given number of term, attribute, and feature
     * slots.
     *
     * @param numTerms number of term slots
     * @param numAttributes number of attribute slots
     * @param numFeatures number of feature slots
     **/
    explicit MatchData(const Params &cparams);

    MatchData(const MatchData &rhs) = delete;
    MatchData & operator=(const MatchData &rhs) = delete;

    /**
     * A number in the range [0,1] indicating how much of the corpus
     * the query must match for termwise evaluation to be enabled. 1
     * means never allowed. 0 means always allowed. The initial value
     * is 1 (never). This value is used when creating a search
     * (queryeval::Blueprint::createSearch).
     **/
    double get_termwise_limit() const { return _termwise_limit; }
    void set_termwise_limit(double value) { _termwise_limit = value; }

    /**
     * Obtain the number of term fields allocated in this match data
     * structure.
     *
     * @return number of term fields allocated
     **/
    uint32_t getNumTermFields() const { return _termFields.size(); }

    /**
     * Obtain the number of features allocated in this match data
     * structure.
     *
     * @return number of features allocated
     **/
    uint32_t getNumFeatures() const { return _features.size(); }

    /**
     * Resolve a term field handle into a pointer to the actual data.
     *
     * @return term field match data
     * @param handle term field handle
     **/
    TermFieldMatchData *resolveTermField(TermFieldHandle handle) { return &_termFields[handle]; }

    /**
     * Resolve a term field handle into a pointer to the actual data.
     *
     * @return term field match data
     * @param handle term field handle
     **/
    const TermFieldMatchData *resolveTermField(TermFieldHandle handle) const { return &_termFields[handle]; }

    /**
     * Resolve a feature handle into a pointer to the actual data.
     * This is used to resolve both {@link FeatureExecutor#inputs}
     * and {@link FeatureExecutor#outputs}.
     *
     * @return feature location
     * @param handle feature handle
     **/
    feature_t *resolveFeature(FeatureHandle handle) { return &_features[handle].as_number; }

    /**
     * Resolve a feature handle into a pointer to the actual data.
     * This is used to resolve both {@link FeatureExecutor#inputs}
     * and {@link FeatureExecutor#outputs}.
     *
     * @return feature location
     * @param handle feature handle
     **/
    const feature_t *resolveFeature(FeatureHandle handle) const { return &_features[handle].as_number; }

    void tag_feature_as_object(FeatureHandle handle) { _feature_is_object[handle] = true; }
    bool feature_is_object(FeatureHandle handle) const { return _feature_is_object[handle]; }

    vespalib::eval::Value::CREF *resolve_object_feature(FeatureHandle handle) {
        assert(_feature_is_object[handle]);
        return &_features[handle].as_object;
    }

    const vespalib::eval::Value::CREF *resolve_object_feature(FeatureHandle handle) const {
        assert(_feature_is_object[handle]);
        return &_features[handle].as_object;
    }

    const NumberOrObject *resolve_raw(FeatureHandle handle) const { return &_features[handle]; }

    static MatchData::UP makeTestInstance(uint32_t numFeatures, uint32_t numHandles, uint32_t fieldIdLimit);
};

} // namespace fef
} // namespace search
