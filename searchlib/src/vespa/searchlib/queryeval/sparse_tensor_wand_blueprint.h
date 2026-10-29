// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blueprint.h"
#include "sparse_tensor_wand_search.h"
#include "wand/weak_and_heap.h"

namespace search::attribute {
class IAttributeVector;
}
namespace search::tensor {
class ITensorAttribute;
}
namespace vespalib::eval {
class InterpretedFunction;
struct Value;
} // namespace vespalib::eval

namespace search::queryeval {

/**
 * Blueprint for the sparse tensor WAND search iterator.
 *
 * Matches the documents with the highest dot product between a sparse document
 * tensor and a sparse query tensor, both with the same tensor type having exactly
 * one mapped dimension. A heap of the best target hits scores is used to gradually
 * raise the score threshold, like the parallel WAND operator does.
 */
class SparseTensorWandBlueprint : public ComplexLeafBlueprint {
private:
    const tensor::ITensorAttribute&                      _attr_tensor;
    const vespalib::eval::Value&                         _query_tensor;
    std::unique_ptr<vespalib::eval::InterpretedFunction> _interpreted_dot_product; // fast-search attributes
    std::unique_ptr<SparseTensorDotProduct>              _dot_product;             // other attributes
    uint32_t                                             _target_hits;
    double                                               _score_threshold;
    mutable SharedWeakAndPriorityQueueT<double>          _scores;
    MatchingPhase                                        _matching_phase;

public:
    SparseTensorWandBlueprint(const FieldSpec& field, const tensor::ITensorAttribute& attr_tensor,
                              const vespalib::eval::Value& query_tensor, uint32_t target_hits,
                              double score_threshold);
    SparseTensorWandBlueprint(const SparseTensorWandBlueprint&) = delete;
    SparseTensorWandBlueprint& operator=(const SparseTensorWandBlueprint&) = delete;
    ~SparseTensorWandBlueprint() override;

    /**
     * Checks that the attribute is a tensor attribute with a sparse tensor type
     * with exactly one mapped dimension, and that the query tensor has the same type.
     * Throws vespalib::IllegalArgumentException if not.
     */
    static const tensor::ITensorAttribute& validate(const attribute::IAttributeVector& attr,
                                                    const vespalib::eval::Value&       query_tensor);

    uint32_t get_target_hits() const noexcept { return _target_hits; }
    double get_score_threshold() const noexcept { return _score_threshold; }

    double sort(InFlow in_flow) override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override {
        return default_flow_stats(docid_limit, getState().estimate().estHits, 0);
    }
    std::unique_ptr<SearchIterator>
    createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda) const override;
    SearchIteratorUP createFilterSearchImpl(FilterConstraint constraint) const override {
        return create_default_filter(constraint);
    }
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    bool always_needs_unpack() const override;
    void set_matching_phase(MatchingPhase matching_phase) noexcept override;
};

} // namespace search::queryeval
