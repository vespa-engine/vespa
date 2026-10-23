// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"

#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/lazy_params.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/util/priority_queue.h>

#include <atomic>
#include <mutex>

namespace search::tensor {
class ITensorAttribute;
}
namespace vespalib::eval {
class ValueType;
struct Value;
} // namespace vespalib::eval

namespace search::queryeval {

/**
 * A heap of the K best scores that can be shared between multiple search iterators.
 * The score threshold starts at the given initial value, and is raised to the
 * lowest score in the heap whenever the heap is full.
 **/
class SparseTensorWandHeap {
private:
    std::mutex                      _lock;
    size_t                          _size;
    std::atomic<double>             _score_threshold;
    vespalib::PriorityQueue<double> _priQ;

public:
    SparseTensorWandHeap(size_t maxSize, double score_threshold)
        : _size(maxSize), _score_threshold(score_threshold), _priQ() {
        _priQ.reserve(maxSize);
    }
    void set_score_threshold(double score_threshold) {
        _score_threshold.store(score_threshold, std::memory_order_relaxed);
    }
    double score_threshold() const { return _score_threshold.load(std::memory_order_relaxed); }
    void used(double score) {
        std::lock_guard<std::mutex> guard(_lock);
        if (_priQ.size() < _size) {
            _priQ.push(score);
        } else if (_size > 0 && score > _priQ.front()) {
            _priQ.front() = score;
            _priQ.adjust();
        }
        if (_size > 0 && _priQ.size() >= _size) {
            if (_score_threshold.load(std::memory_order_relaxed) < _priQ.front()) {
                _score_threshold.store(_priQ.front(), std::memory_order_relaxed);
            }
        }
    }
};

/**
 * Evaluates the dot product between document tensors and a fixed query tensor,
 * using a function compiled by SparseTensorWandSearch::make_dot_product.
 * Keeps evaluation state, so each thread needs its own instance.
 **/
class SparseTensorDotProduct {
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
    SparseTensorDotProduct(const vespalib::eval::InterpretedFunction& function,
                           const vespalib::eval::Value&               query_tensor);
    ~SparseTensorDotProduct();
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
    // Whether the given type is a sparse tensor type with exactly one mapped dimension.
    static bool is_supported_type(const vespalib::eval::ValueType& type);

    // Compiles reduce(join(a,b,f(x,y)(x*y)),sum) for two tensors of the given type.
    static std::unique_ptr<vespalib::eval::InterpretedFunction>
    make_dot_product(const vespalib::eval::ValueType& type);

    static std::unique_ptr<SearchIterator> create(bool strict, fef::TermFieldMatchData& tfmd,
                                                  const tensor::ITensorAttribute&            attr_tensor,
                                                  const vespalib::eval::InterpretedFunction& dot_product,
                                                  const vespalib::eval::Value&               query_tensor,
                                                  SparseTensorWandHeap& scores, bool readonly_scores_heap);
};

} // namespace search::queryeval
