// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_wand_search.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/node_types.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>

#include <vector>

using search::tensor::ITensorAttribute;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::Function;
using vespalib::eval::InterpretedFunction;
using vespalib::eval::NodeTypes;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::queryeval {

namespace {

/**
 * Document tensor access by reference (fast-search attributes and streaming search).
 */
class TensorRefAccess {
    const ITensorAttribute& _attr;

public:
    explicit TensorRefAccess(const ITensorAttribute& attr) : _attr(attr) {}
    const Value& get(uint32_t docid) { return _attr.get_tensor_ref(docid); }
};

/**
 * Document tensor access by creating a value (view) for each document.
 */
class TensorCopyAccess {
    const ITensorAttribute& _attr;
    std::unique_ptr<Value>  _empty;
    std::unique_ptr<Value>  _tensor;

public:
    explicit TensorCopyAccess(const ITensorAttribute& attr) : _attr(attr), _empty(attr.getEmptyTensor()), _tensor() {}
    ~TensorCopyAccess();
    const Value& get(uint32_t docid) {
        _tensor = _attr.getTensor(docid);
        return _tensor ? *_tensor : *_empty;
    }
};

TensorCopyAccess::~TensorCopyAccess() = default;

template <bool strict, typename DocAccess> class SparseTensorWandSearchImpl final : public SparseTensorWandSearch {
    fef::TermFieldMatchData& _tfmd;
    DocAccess                _doc_access;
    SparseTensorDotProduct   _dot_product;
    const MatchParams        _match_params;
    std::vector<double>      _local_scores;
    const bool               _readonly_scores_heap;
    double                   _last_score;

    double score(uint32_t docid) { return _dot_product.eval(_doc_access.get(docid)); }

public:
    SparseTensorWandSearchImpl(fef::TermFieldMatchData& tfmd, const ITensorAttribute& attr,
                               const InterpretedFunction& dot_product, const Value& query_tensor,
                               const MatchParams& match_params, bool readonly_scores_heap)
        : _tfmd(tfmd),
          _doc_access(attr),
          _dot_product(dot_product, query_tensor),
          _match_params(match_params),
          _local_scores(),
          _readonly_scores_heap(readonly_scores_heap),
          _last_score(0.0) {
        _local_scores.reserve(_match_params.scoresAdjustFrequency);
    }

    void doSeek(uint32_t docid) override {
        double threshold = _match_params.scores.getMinScore();
        while (__builtin_expect((docid < getEndId()), true)) {
            double s = score(docid);
            if (s > threshold) {
                _last_score = s;
                setDocId(docid);
                return;
            }
            if (strict) {
                ++docid;
            } else {
                return;
            }
        }
        setAtEnd();
    }

    void doUnpack(uint32_t docid) override {
        _tfmd.setRawScore(docid, _last_score);
        if (!_readonly_scores_heap) {
            _local_scores.push_back(_last_score);
            if (_local_scores.size() == _match_params.scoresAdjustFrequency) {
                _match_params.scores.adjust(_local_scores);
                _local_scores.clear();
            }
        }
    }

    Trinary is_strict() const override { return strict ? Trinary::True : Trinary::False; }
};

template <typename DocAccess>
std::unique_ptr<SearchIterator> create_with_access(bool strict, fef::TermFieldMatchData& tfmd,
                                                   const ITensorAttribute&    attr,
                                                   const InterpretedFunction& dot_product, const Value& query_tensor,
                                                   const SparseTensorWandSearch::MatchParams& match_params,
                                                   bool readonly_scores_heap) {
    if (strict) {
        return std::make_unique<SparseTensorWandSearchImpl<true, DocAccess>>(tfmd, attr, dot_product, query_tensor,
                                                                             match_params, readonly_scores_heap);
    } else {
        return std::make_unique<SparseTensorWandSearchImpl<false, DocAccess>>(tfmd, attr, dot_product, query_tensor,
                                                                              match_params, readonly_scores_heap);
    }
}

} // namespace

SparseTensorDotProduct::SparseTensorDotProduct(const InterpretedFunction& function, const Value& query_tensor)
    : _function(function), _ctx(function), _params(query_tensor) {
}

SparseTensorDotProduct::~SparseTensorDotProduct() = default;

bool SparseTensorWandSearch::is_supported_type(const ValueType& type) {
    return type.is_sparse() && (type.count_mapped_dimensions() == 1);
}

std::unique_ptr<InterpretedFunction> SparseTensorWandSearch::make_dot_product(const ValueType& type) {
    auto      function = Function::parse({"a", "b"}, "reduce(join(a,b,f(x,y)(x*y)),sum)");
    NodeTypes types(*function, {type, type});
    return std::make_unique<InterpretedFunction>(FastValueBuilderFactory::get(), *function, types);
}

std::unique_ptr<SearchIterator>
SparseTensorWandSearch::create(bool strict, fef::TermFieldMatchData& tfmd, const ITensorAttribute& attr_tensor,
                               const InterpretedFunction& dot_product, const Value& query_tensor,
                               const MatchParams& match_params, bool readonly_scores_heap) {
    if (attr_tensor.supports_get_tensor_ref()) {
        return create_with_access<TensorRefAccess>(strict, tfmd, attr_tensor, dot_product, query_tensor, match_params,
                                                   readonly_scores_heap);
    }
    return create_with_access<TensorCopyAccess>(strict, tfmd, attr_tensor, dot_product, query_tensor, match_params,
                                                readonly_scores_heap);
}

} // namespace search::queryeval
