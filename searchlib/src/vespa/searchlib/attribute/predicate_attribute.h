// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"
#include <vespa/searchlib/predicate/common.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace document { class PredicateFieldValue; }

namespace search::predicate { class PredicateIndex; }

namespace search {

struct AttributeVectorDocIdLimitProvider : public predicate::DocIdLimitProvider {
    AttributeVectorDocIdLimitProvider(const AttributeVector &attribute_vector) :
            _attribute_vector(attribute_vector) {}

    uint32_t getDocIdLimit() const override { return _attribute_vector.getNumDocs(); };
    uint32_t getCommittedDocIdLimit() const override {
        return _attribute_vector.getCommittedDocIdLimit();
    }
private:
    const AttributeVector &_attribute_vector;
};

/**
 * Attribute that manages a predicate index. It is not a traditional
 * attribute in that it doesn't store values for each document, but
 * rather keeps an index for predicate search. Summaries are not fetched
 * from the attribute, but rather using the summary store like a
 * non-index field.
 */
class PredicateAttribute : public NotImplementedAttribute {
public:
    typedef uint8_t MinFeature;
    typedef std::pair<const MinFeature *, size_t> MinFeatureHandle;
    using IntervalRange = uint16_t;
    using IntervalRangeVector = vespalib::RcuVectorBase<IntervalRange>;

    PredicateAttribute(const vespalib::string &base_file_name);
    PredicateAttribute(const vespalib::string &base_file_name, const Config &config);

    ~PredicateAttribute() override;

    predicate::PredicateIndex &getIndex() { return *_index; }

    void onSave(IAttributeSaveTarget & saveTarget) override;
    bool onLoad(vespalib::Executor *executor) override;
    void onCommit() override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    void onUpdateStat() override;
    bool addDoc(DocId &doc_id) override;
    uint32_t clearDoc(DocId doc_id) override;
    uint32_t getValueCount(DocId doc) const override;

    void updateValue(uint32_t doc_id, const document::PredicateFieldValue &value);

    /**
     * Will return a handle with a pointer to the min_features and how many there are.
     * The pointer is only guaranteed to be valid for as long as you hold the attribute guard.
     **/
    MinFeatureHandle getMinFeatureVector() const {
        const auto* min_feature_vector = &_min_feature.acquire_elem_ref(0);
        return MinFeatureHandle(min_feature_vector, getNumDocs());
    }

    const IntervalRange * getIntervalRangeVector() const {
        return &_interval_range_vector.acquire_elem_ref(0);
    }

    IntervalRange getMaxIntervalRange() const {
        return _max_interval_range;
    }

    void updateMaxIntervalRange(IntervalRange intervalRange) {
        _max_interval_range = std::max(intervalRange, _max_interval_range);
    }

    void populateIfNeeded();
private:
    const AttributeVectorDocIdLimitProvider _limit_provider;
    std::unique_ptr<predicate::PredicateIndex> _index;
    int64_t _lower_bound;
    int64_t _upper_bound;

    typedef vespalib::RcuVectorBase<uint8_t> MinFeatureVector;
    MinFeatureVector _min_feature;

    IntervalRangeVector _interval_range_vector;
    IntervalRange _max_interval_range;
public:
    static constexpr uint8_t MIN_FEATURE_FILL = 255;
    static constexpr uint32_t PREDICATE_ATTRIBUTE_VERSION = 2;

    uint32_t getVersion() const override;
};

}  // namespace search
