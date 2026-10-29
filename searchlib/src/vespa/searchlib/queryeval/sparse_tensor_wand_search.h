// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "wand/weak_and_heap.h"

#include <vespa/searchlib/fef/termfieldmatchdata.h>

#include <memory>
#include <vector>

namespace search::tensor {
class ITensorAttribute;
class SerializedTensorRef;
} // namespace search::tensor
namespace vespalib::eval {
class FastAddrMap;
class ValueType;
struct Value;
} // namespace vespalib::eval

namespace search::queryeval {

/**
 * Calculates the dot product between document tensors and a fixed query tensor,
 * both sparse tensors with the same type having exactly one mapped dimension.
 * The labels of the document tensor are looked up directly in the hash table of
 * the query tensor. Has no mutable state, so one instance can be shared between threads.
 **/
class SparseTensorDotProduct {
private:
    std::unique_ptr<vespalib::eval::Value> _query_copy; // only used when query tensor is not a fast value
    const vespalib::eval::FastAddrMap*     _query_map;
    std::vector<double>                    _query_cells;
    std::vector<float>                     _query_float_cells; // only set when query cells are float

public:
    explicit SparseTensorDotProduct(const vespalib::eval::Value& query_tensor);
    SparseTensorDotProduct(const SparseTensorDotProduct&) = delete;
    SparseTensorDotProduct& operator=(const SparseTensorDotProduct&) = delete;
    ~SparseTensorDotProduct();
    double eval(const vespalib::eval::Value& doc_tensor) const;
    double eval(const tensor::SerializedTensorRef& doc_tensor) const;
};

/**
 * Search iterator matching documents where the dot product between a sparse
 * document tensor and a sparse query tensor exceeds the current score threshold.
 * Uses unpack() as feedback mechanism to track which matches actually became hits,
 * raising the threshold in the shared heap like the parallel WAND iterator does.
 * Since tensor attributes have no posting lists, all documents are scanned.
 **/
class SparseTensorWandSearch : public SearchIterator {
public:
    using MatchParams = wand::CommonMatchParamsT<double>;

    // Whether the given type is a sparse tensor type with exactly one mapped dimension.
    static bool is_supported_type(const vespalib::eval::ValueType& type);

    static std::unique_ptr<SearchIterator> create(bool strict, fef::TermFieldMatchData& tfmd,
                                                  const tensor::ITensorAttribute& attr_tensor,
                                                  const SparseTensorDotProduct&   dot_product,
                                                  const MatchParams& match_params, bool readonly_scores_heap);
};

} // namespace search::queryeval
