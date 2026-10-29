// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "wand/weak_and_heap.h"

#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/lazy_params.h>
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
 * Evaluates the dot product between document tensors and a fixed query tensor,
 * using a function compiled by SparseTensorWandSearch::make_dot_product.
 * Used for fast-search attributes, where this is faster than SparseTensorDotProduct.
 * Keeps evaluation state, so each thread needs its own instance.
 **/
class InterpretedSparseTensorDotProduct {
private:
    // The document tensor changes for each document while the query tensor stays the same.
    struct Params : vespalib::eval::LazyParams {
        const vespalib::eval::Value* doc_tensor;
        const vespalib::eval::Value& query_tensor;
        explicit Params(const vespalib::eval::Value& query_tensor_in)
            : doc_tensor(nullptr), query_tensor(query_tensor_in) {}
        const vespalib::eval::Value& resolve(size_t idx, vespalib::Stash&) const override {
            return (idx == 0) ? *doc_tensor : query_tensor;
        }
    };
    const vespalib::eval::InterpretedFunction&   _function;
    vespalib::eval::InterpretedFunction::Context _ctx;
    Params                                       _params;

public:
    InterpretedSparseTensorDotProduct(const vespalib::eval::InterpretedFunction& function,
                                      const vespalib::eval::Value&               query_tensor);
    ~InterpretedSparseTensorDotProduct();
    double eval(const vespalib::eval::Value& doc_tensor) {
        _params.doc_tensor = &doc_tensor;
        return _function.eval(_ctx, _params).as_double();
    }
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

    // Compiles reduce(join(a,b,f(x,y)(x*y)),sum) for two tensors of the given type.
    static std::unique_ptr<vespalib::eval::InterpretedFunction>
    make_dot_product(const vespalib::eval::ValueType& type);

    // Calculates the dot product directly (attributes without fast-search).
    static std::unique_ptr<SearchIterator> create(bool strict, fef::TermFieldMatchData& tfmd,
                                                  const tensor::ITensorAttribute& attr_tensor,
                                                  const SparseTensorDotProduct&   dot_product,
                                                  const MatchParams& match_params, bool readonly_scores_heap);

    // Evaluates the dot product with an interpreted function (fast-search attributes).
    static std::unique_ptr<SearchIterator> create(bool strict, fef::TermFieldMatchData& tfmd,
                                                  const tensor::ITensorAttribute&            attr_tensor,
                                                  const vespalib::eval::InterpretedFunction& dot_product,
                                                  const vespalib::eval::Value&               query_tensor,
                                                  const MatchParams& match_params, bool readonly_scores_heap);
};

} // namespace search::queryeval
