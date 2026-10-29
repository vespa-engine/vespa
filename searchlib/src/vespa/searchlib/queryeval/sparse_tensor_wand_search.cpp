// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_wand_search.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/fast_value_index.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/node_types.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/searchlib/tensor/serialized_tensor_ref.h>
#include <vespa/vespalib/util/typify.h>

#include <type_traits>
#include <utility>
#include <vector>

using search::tensor::ITensorAttribute;
using search::tensor::SerializedTensorRef;
using vespalib::string_id;
using vespalib::typify_invoke;
using vespalib::eval::CellType;
using vespalib::eval::FastAddrMap;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::FastValueIndex;
using vespalib::eval::Function;
using vespalib::eval::InterpretedFunction;
using vespalib::eval::NodeTypes;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::queryeval {

namespace {

const FastAddrMap* as_fast_map(const Value::Index& index) {
    const auto* fast_index = dynamic_cast<const FastValueIndex*>(&index);
    return (fast_index != nullptr) ? &fast_index->map : nullptr;
}

struct ConvertCells {
    template <typename CT, typename DST> static void invoke(TypedCells cells, std::vector<DST>& dst) {
        auto src = cells.typify<CT>();
        dst.assign(src.begin(), src.end());
    }
};

// document labels are stored in subspace order
template <typename QT, typename CT>
double calc_dot_product(const FastAddrMap& query_map, const QT* query_cells, std::span<const string_id> labels,
                        const CT* cells) {
    double result = 0.0;
    for (size_t i = 0; i < labels.size(); ++i) {
        auto query_subspace = query_map.lookup_singledim(labels[i]);
        if (query_subspace != FastAddrMap::npos()) {
            result += query_cells[query_subspace] * cells[i];
        }
    }
    return result;
}

// fallback for document tensors that are not fast values
template <typename QT, typename CT>
double calc_dot_product(const FastAddrMap& query_map, const QT* query_cells, const Value::Index& doc_index,
                        const CT* cells) {
    double     result = 0.0;
    string_id  label;
    string_id* label_ptr = &label;
    size_t     doc_subspace = 0;
    auto       view = doc_index.create_view({});
    view->lookup({});
    while (view->next_result({&label_ptr, 1}, doc_subspace)) {
        auto query_subspace = query_map.lookup_singledim(label);
        if (query_subspace != FastAddrMap::npos()) {
            result += query_cells[query_subspace] * cells[doc_subspace];
        }
    }
    return result;
}

struct CalcDotProduct {
    template <typename CT, typename DocLabels>
    static double invoke(const FastAddrMap& query_map, const std::vector<double>& query_cells,
                         const std::vector<float>& query_float_cells, const DocLabels& doc_labels,
                         TypedCells doc_cells) {
        const CT* cells = doc_cells.typify<CT>().data();
        if constexpr (std::is_same_v<CT, float>) {
            // multiply float cells directly when both query and document cells are float
            if (!query_float_cells.empty()) {
                return calc_dot_product(query_map, query_float_cells.data(), doc_labels, cells);
            }
        }
        return calc_dot_product(query_map, query_cells.data(), doc_labels, cells);
    }
};

/**
 * Document tensor access by reference to a value (fast-search attributes and streaming search).
 */
class TensorRefAccess {
    const ITensorAttribute& _attr;

public:
    explicit TensorRefAccess(const ITensorAttribute& attr) : _attr(attr) {}
    const Value& get(uint32_t docid) const { return _attr.get_tensor_ref(docid); }
};

/**
 * Document tensor access by reference to the serialized tensor (labels and cells) in a tensor buffer store.
 */
class SerializedTensorRefAccess {
    const ITensorAttribute& _attr;

public:
    explicit SerializedTensorRefAccess(const ITensorAttribute& attr) : _attr(attr) {}
    SerializedTensorRef get(uint32_t docid) const { return _attr.get_serialized_tensor_ref(docid); }
};

/**
 * Scores documents by calculating the dot product directly.
 */
template <typename DocAccess> class DirectScorer {
    DocAccess                     _doc_access;
    const SparseTensorDotProduct& _dot_product;

public:
    DirectScorer(const ITensorAttribute& attr, const SparseTensorDotProduct& dot_product)
        : _doc_access(attr), _dot_product(dot_product) {}
    double score(uint32_t docid) { return _dot_product.eval(_doc_access.get(docid)); }
};

/**
 * Scores documents by evaluating the dot product with an interpreted function (fast-search attributes).
 */
class InterpretedScorer {
    TensorRefAccess                   _doc_access;
    InterpretedSparseTensorDotProduct _dot_product;

public:
    InterpretedScorer(const ITensorAttribute& attr, const InterpretedFunction& dot_product, const Value& query_tensor)
        : _doc_access(attr), _dot_product(dot_product, query_tensor) {}
    double score(uint32_t docid) { return _dot_product.eval(_doc_access.get(docid)); }
};

template <bool strict, typename Scorer> class SparseTensorWandSearchImpl final : public SparseTensorWandSearch {
    fef::TermFieldMatchData& _tfmd;
    Scorer                   _scorer;
    const MatchParams        _match_params;
    std::vector<double>      _local_scores;
    const bool               _readonly_scores_heap;
    double                   _last_score;

    double score(uint32_t docid) { return _scorer.score(docid); }

public:
    template <typename... ScorerArgs>
    SparseTensorWandSearchImpl(fef::TermFieldMatchData& tfmd, const MatchParams& match_params,
                               bool readonly_scores_heap, ScorerArgs&&... scorer_args)
        : _tfmd(tfmd),
          _scorer(std::forward<ScorerArgs>(scorer_args)...),
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

template <typename Scorer, typename... ScorerArgs>
std::unique_ptr<SearchIterator> create_with_scorer(bool strict, fef::TermFieldMatchData& tfmd,
                                                   const SparseTensorWandSearch::MatchParams& match_params,
                                                   bool readonly_scores_heap, ScorerArgs&&... scorer_args) {
    if (strict) {
        return std::make_unique<SparseTensorWandSearchImpl<true, Scorer>>(tfmd, match_params, readonly_scores_heap,
                                                                          std::forward<ScorerArgs>(scorer_args)...);
    } else {
        return std::make_unique<SparseTensorWandSearchImpl<false, Scorer>>(tfmd, match_params, readonly_scores_heap,
                                                                           std::forward<ScorerArgs>(scorer_args)...);
    }
}

} // namespace

SparseTensorDotProduct::SparseTensorDotProduct(const Value& query_tensor)
    : _query_copy(), _query_map(as_fast_map(query_tensor.index())), _query_cells(), _query_float_cells() {
    const Value* query = &query_tensor;
    if (_query_map == nullptr) {
        _query_copy = FastValueBuilderFactory::get().copy(query_tensor);
        query = _query_copy.get();
        _query_map = as_fast_map(query->index());
    }
    auto cells = query->cells();
    typify_invoke<1, TypifyCellType, ConvertCells>(cells.type, cells, _query_cells);
    if (cells.type == CellType::FLOAT) {
        typify_invoke<1, TypifyCellType, ConvertCells>(cells.type, cells, _query_float_cells);
    }
}

SparseTensorDotProduct::~SparseTensorDotProduct() = default;

double SparseTensorDotProduct::eval(const Value& doc_tensor) const {
    auto doc_cells = doc_tensor.cells();
    if (const auto* doc_map = as_fast_map(doc_tensor.index())) [[likely]] {
        std::span<const string_id> labels(doc_map->labels().data(), doc_map->size());
        return typify_invoke<1, TypifyCellType, CalcDotProduct>(doc_cells.type, *_query_map, _query_cells,
                                                                _query_float_cells, labels, doc_cells);
    }
    return typify_invoke<1, TypifyCellType, CalcDotProduct>(doc_cells.type, *_query_map, _query_cells,
                                                            _query_float_cells, doc_tensor.index(), doc_cells);
}

double SparseTensorDotProduct::eval(const SerializedTensorRef& doc_tensor) const {
    const auto& vectors = doc_tensor.get_vectors();
    if (vectors.subspaces() == 0) {
        return 0.0;
    }
    // each subspace has exactly one cell, so the cells of all subspaces are stored contiguously
    auto       first = vectors.cells(0);
    TypedCells doc_cells(first.data, first.type, vectors.subspaces());
    return typify_invoke<1, TypifyCellType, CalcDotProduct>(doc_cells.type, *_query_map, _query_cells,
                                                            _query_float_cells, doc_tensor.get_labels(), doc_cells);
}

InterpretedSparseTensorDotProduct::InterpretedSparseTensorDotProduct(const InterpretedFunction& function,
                                                                     const Value&               query_tensor)
    : _function(function), _ctx(function), _params(query_tensor) {
}

InterpretedSparseTensorDotProduct::~InterpretedSparseTensorDotProduct() = default;

bool SparseTensorWandSearch::is_supported_type(const ValueType& type) {
    return type.is_sparse() && (type.count_mapped_dimensions() == 1);
}

std::unique_ptr<InterpretedFunction> SparseTensorWandSearch::make_dot_product(const ValueType& type) {
    auto      function = Function::parse({"a", "b"}, "reduce(join(a,b,f(x,y)(x*y)),sum)");
    NodeTypes types(*function, {type, type});
    return std::make_unique<InterpretedFunction>(FastValueBuilderFactory::get(), *function, types);
}

std::unique_ptr<SearchIterator> SparseTensorWandSearch::create(bool strict, fef::TermFieldMatchData& tfmd,
                                                               const ITensorAttribute&       attr_tensor,
                                                               const SparseTensorDotProduct& dot_product,
                                                               const MatchParams&            match_params,
                                                               bool                          readonly_scores_heap) {
    if (attr_tensor.supports_get_tensor_ref()) {
        return create_with_scorer<DirectScorer<TensorRefAccess>>(strict, tfmd, match_params, readonly_scores_heap,
                                                                 attr_tensor, dot_product);
    }
    return create_with_scorer<DirectScorer<SerializedTensorRefAccess>>(strict, tfmd, match_params,
                                                                       readonly_scores_heap, attr_tensor, dot_product);
}

std::unique_ptr<SearchIterator> SparseTensorWandSearch::create(bool strict, fef::TermFieldMatchData& tfmd,
                                                               const ITensorAttribute&    attr_tensor,
                                                               const InterpretedFunction& dot_product,
                                                               const Value& query_tensor, const MatchParams& match_params,
                                                               bool readonly_scores_heap) {
    return create_with_scorer<InterpretedScorer>(strict, tfmd, match_params, readonly_scores_heap, attr_tensor,
                                                 dot_product, query_tensor);
}

} // namespace search::queryeval
