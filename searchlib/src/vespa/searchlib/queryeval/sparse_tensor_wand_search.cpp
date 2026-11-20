// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_wand_search.h"

#include "wand/wand_parts.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/fast_value_index.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/node_types.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/searchlib/tensor/serialized_tensor_ref.h>
#include <vespa/vespalib/util/left_right_heap.h>
#include <vespa/vespalib/util/typify.h>

#include <algorithm>
#include <cassert>
#include <functional>
#include <limits>
#include <queue>
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
 * Document tensor access by reference to a value (fast-rank attributes and streaming search).
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
 * Scores documents by evaluating the dot product with an interpreted function (fast-rank attributes).
 */
class InterpretedScorer {
    TensorRefAccess                   _doc_access;
    InterpretedSparseTensorDotProduct _dot_product;

public:
    InterpretedScorer(const ITensorAttribute& attr, const InterpretedFunction& dot_product, const Value& query_tensor)
        : _doc_access(attr), _dot_product(dot_product, query_tensor) {}
    double score(uint32_t docid) { return _dot_product.eval(_doc_access.get(docid)); }
};

/**
 * Sums the positive values Huffman style: repeatedly add the two smallest
 * values, to minimize the accumulated rounding error.
 */
double huffman_sum_positive(const std::vector<double>& values) {
    std::priority_queue<double, std::vector<double>, std::greater<>> heap;
    for (double value : values) {
        if (value > 0.0) {
            heap.push(value);
        }
    }
    if (heap.empty()) {
        return 0.0;
    }
    while (heap.size() > 1) {
        double a = heap.top();
        heap.pop();
        double b = heap.top();
        heap.pop();
        heap.push(a + b);
    }
    return heap.top();
}

/**
 * Scans all documents, scoring each of them.
 */
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

/**
 * The docid posting lists of the query labels, as terms for wand::AlgorithmT<double>.
 * The score of a term is its contribution to the dot product (query cell * posting weight),
 * and its max score is an upper bound of that, calculated from the min/max posting weight.
 * Terms are ordered like wand::MaxSkipOrder, so the most valuable terms are checked first.
 */
class PostingTerms {
    std::vector<uint32_t>                     _docid; // current docid of each term (as seen by the algorithm)
    std::vector<double>                       _max_score;
    std::vector<double>                       _query_weight;
    std::vector<DocidWithFloatWeightIterator> _children;
    double                                    _total_max_score; // sum of positive max scores

public:
    PostingTerms(std::vector<DocidWithFloatWeightIterator> postings, const std::vector<double>& query_weights,
                 uint32_t docid_limit);
    PostingTerms(const PostingTerms&) = delete;
    PostingTerms& operator=(const PostingTerms&) = delete;
    ~PostingTerms();

    // interface used by wand::AlgorithmT and wand::DualHeap
    uint32_t* docId() noexcept { return _docid.data(); }
    uint32_t& docId(wand::ref_t ref) noexcept { return _docid[ref]; }
    double maxScore(wand::ref_t ref) const noexcept { return _max_score[ref]; }
    size_t size() const noexcept { return _children.size(); }
    uint32_t seek(wand::ref_t ref, uint32_t docid) noexcept {
        _children[ref].linearSeek(docid);
        return get_docid(ref);
    }
    PostingTerms& iteratorPack() noexcept { return *this; }
    void initRange(uint32_t begin, uint32_t) {
        for (auto& child : _children) {
            child.lower_bound(begin);
        }
    }
    uint32_t get_docid(wand::ref_t ref) const noexcept {
        return _children[ref].valid() ? _children[ref].getKey() : endDocId;
    }

    // contribution to the dot product from the given term (positioned at the current candidate)
    double term_score(wand::ref_t ref) const noexcept { return _query_weight[ref] * _children[ref].getData(); }
    double total_max_score() const noexcept { return _total_max_score; }
};

/**
 * Scorer for wand::AlgorithmT using the weights in the posting lists.
 */
struct PostingScorer {
    static double calculateScore(const PostingTerms& terms, wand::ref_t ref, uint32_t) noexcept {
        return terms.term_score(ref);
    }
};

PostingTerms::PostingTerms(std::vector<DocidWithFloatWeightIterator> postings,
                           const std::vector<double>& query_weights, uint32_t docid_limit)
    : _docid(), _max_score(), _query_weight(), _children(), _total_max_score(0.0) {
    assert(query_weights.size() == postings.size());
    static_assert(SparseTensorWandSearch::max_postings == std::numeric_limits<wand::ref_t>::max());
    assert(postings.size() <= SparseTensorWandSearch::max_postings);
    std::vector<double> max_scores;
    std::vector<double> est_hits;
    max_scores.reserve(postings.size());
    est_hits.reserve(postings.size());
    double est_num_docs = std::max(1.0, docid_limit - 1.0);
    // posting lists are always btrees here, so the min/max weight aggregates are available
    for (uint32_t ref = 0; ref < postings.size(); ++ref) {
        const auto& aggr = postings[ref].getAggregated();
        double      qw = query_weights[ref];
        // the largest qw * w for any document weight w in [min, max]
        double upper_bound = (qw >= 0.0) ? qw * aggr.getMax() : qw * aggr.getMin();
        size_t size = postings[ref].size();
        // a term that can only lower the score never helps a document pass the threshold
        max_scores.push_back(std::max(upper_bound, 0.0));
        est_hits.push_back(size);
        est_num_docs = std::max(est_num_docs, double(size));
    }
    // a document can match any subset of the terms, so only positive contributions raise the max score
    _total_max_score = huffman_sum_positive(max_scores);
    std::vector<uint32_t> order(postings.size());
    for (uint32_t ref = 0; ref < order.size(); ++ref) {
        order[ref] = ref;
    }
    auto skip_value = [&](uint32_t ref) { return ((est_num_docs - est_hits[ref]) / est_num_docs) * max_scores[ref]; };
    std::stable_sort(order.begin(), order.end(),
                     [&](uint32_t a, uint32_t b) { return skip_value(a) > skip_value(b); });
    _docid.reserve(order.size());
    _max_score.reserve(order.size());
    _query_weight.reserve(order.size());
    _children.reserve(order.size());
    for (uint32_t ref : order) {
        _children.push_back(std::move(postings[ref]));
        _max_score.push_back(max_scores[ref]);
        _query_weight.push_back(query_weights[ref]);
        _docid.push_back(get_docid(_children.size() - 1));
    }
}

PostingTerms::~PostingTerms() = default;

/**
 * Strict iterator using the WAND algorithm (see wand::AlgorithmT) on the posting lists of the query labels,
 * like the parallel WAND iterator does. Candidates are found using an upper bound of the contribution from
 * each posting list, and checked using the weights in the posting lists (with early exit when the remaining
 * upper bounds cannot reach the threshold). Only candidates passing that check are scored using the document
 * tensor, and that exact score decides whether the candidate is a hit.
 *
 * The posting weights are float, and the sums are calculated in a different order than the exact score,
 * so the checks using posting weights are done against the threshold lowered by a small slack, to avoid
 * losing hits due to rounding.
 */
template <typename Scorer, typename FutureHeap, typename PastHeap>
class SparseTensorWandPostingSearch final : public SparseTensorWandSearch {
    using Heaps = wand::DualHeap<FutureHeap, PastHeap>;

    fef::TermFieldMatchData& _tfmd;
    Scorer                   _scorer;
    PostingTerms             _terms;
    Heaps                    _heaps;
    wand::AlgorithmT<double> _algo;
    const MatchParams        _match_params;
    std::vector<double>      _local_scores;
    const bool               _readonly_scores_heap;
    // Upper bounds and posting scores may have different rounding errors than the exact score (float
    // posting weights, different summation order). Lower the threshold used for them a bit to stay on
    // the safe side.
    const double _slack;
    double       _last_score;

public:
    template <typename... ScorerArgs>
    SparseTensorWandPostingSearch(fef::TermFieldMatchData& tfmd, std::vector<DocidWithFloatWeightIterator> postings,
                                  const std::vector<double>& query_weights, const MatchParams& match_params,
                                  bool readonly_scores_heap, ScorerArgs&&... scorer_args)
        : _tfmd(tfmd),
          _scorer(std::forward<ScorerArgs>(scorer_args)...),
          _terms(std::move(postings), query_weights, match_params.docid_limit),
          _heaps(wand::DocIdOrder(_terms.docId()), _terms.size()),
          _algo(),
          _match_params(match_params),
          _local_scores(),
          _readonly_scores_heap(readonly_scores_heap),
          _slack(_terms.total_max_score() * 1e-6),
          _last_score(0.0) {
        _local_scores.reserve(_match_params.scoresAdjustFrequency);
    }

    void initRange(uint32_t begin_id, uint32_t end_id) override {
        SparseTensorWandSearch::initRange(begin_id, end_id);
        _algo.init_range(_terms, _heaps, begin_id, end_id);
    }

    void doSeek(uint32_t docid) override {
        double threshold = _match_params.scores.getMinScore();
        // the threshold is never negative here, and a document can only score above it
        // if at least one of its posting lists has a positive upper bound.
        double bound = std::max(threshold - _slack, 0.0);
        auto   above_bound = [bound](double upper_bound) noexcept { return upper_bound > bound; };
        _algo.set_candidate(_terms, _heaps, docid);
        while (_algo.solve_wand_constraint(_terms, _heaps, above_bound)) {
            uint32_t candidate = _algo.get_candidate();
            if (candidate >= getEndId()) {
                break;
            }
            // check_score may stop early when the partial score is above the bound,
            // so the remaining terms are included before comparing with the bound.
            if (_algo.check_score(_terms, _heaps, PostingScorer(), above_bound) &&
                above_bound(_algo.get_full_score(_terms, _heaps, PostingScorer())))
            {
                double s = _scorer.score(candidate);
                if (s > threshold) {
                    _last_score = s;
                    setDocId(candidate);
                    return;
                }
            }
            _algo.set_candidate(_terms, _heaps, candidate + 1);
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

    Trinary is_strict() const override { return Trinary::True; }
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

template <typename Scorer, typename... ScorerArgs>
std::unique_ptr<SearchIterator>
create_with_postings_and_scorer(fef::TermFieldMatchData& tfmd, std::vector<DocidWithFloatWeightIterator> postings,
                                const std::vector<double>&                 query_weights,
                                const SparseTensorWandSearch::MatchParams& match_params, bool readonly_scores_heap,
                                ScorerArgs&&... scorer_args) {
    // same choice of heap implementation as for parallel wand
    if (postings.size() < 128) {
        return std::make_unique<
            SparseTensorWandPostingSearch<Scorer, vespalib::LeftArrayHeap, vespalib::RightArrayHeap>>(
            tfmd, std::move(postings), query_weights, match_params, readonly_scores_heap,
            std::forward<ScorerArgs>(scorer_args)...);
    }
    return std::make_unique<SparseTensorWandPostingSearch<Scorer, vespalib::LeftHeap, vespalib::RightHeap>>(
        tfmd, std::move(postings), query_weights, match_params, readonly_scores_heap,
        std::forward<ScorerArgs>(scorer_args)...);
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
    return create_with_scorer<DirectScorer<SerializedTensorRefAccess>>(
        strict, tfmd, match_params, readonly_scores_heap, attr_tensor, dot_product);
}

std::unique_ptr<SearchIterator>
SparseTensorWandSearch::create(bool strict, fef::TermFieldMatchData& tfmd, const ITensorAttribute& attr_tensor,
                               const InterpretedFunction& dot_product, const Value& query_tensor,
                               const MatchParams& match_params, bool readonly_scores_heap) {
    return create_with_scorer<InterpretedScorer>(strict, tfmd, match_params, readonly_scores_heap, attr_tensor,
                                                 dot_product, query_tensor);
}

std::unique_ptr<SearchIterator> SparseTensorWandSearch::create_with_postings(
    fef::TermFieldMatchData& tfmd, const ITensorAttribute& attr_tensor, const SparseTensorDotProduct& dot_product,
    const MatchParams& match_params, bool readonly_scores_heap, std::vector<DocidWithFloatWeightIterator> postings,
    std::vector<double> query_weights) {
    if (attr_tensor.supports_get_tensor_ref()) {
        return create_with_postings_and_scorer<DirectScorer<TensorRefAccess>>(
            tfmd, std::move(postings), std::move(query_weights), match_params, readonly_scores_heap, attr_tensor,
            dot_product);
    }
    return create_with_postings_and_scorer<DirectScorer<SerializedTensorRefAccess>>(
        tfmd, std::move(postings), std::move(query_weights), match_params, readonly_scores_heap, attr_tensor,
        dot_product);
}

std::unique_ptr<SearchIterator> SparseTensorWandSearch::create_with_postings(
    fef::TermFieldMatchData& tfmd, const ITensorAttribute& attr_tensor, const InterpretedFunction& dot_product,
    const Value& query_tensor, const MatchParams& match_params, bool readonly_scores_heap,
    std::vector<DocidWithFloatWeightIterator> postings, std::vector<double> query_weights) {
    return create_with_postings_and_scorer<InterpretedScorer>(tfmd, std::move(postings), std::move(query_weights),
                                                              match_params, readonly_scores_heap, attr_tensor,
                                                              dot_product, query_tensor);
}

} // namespace search::queryeval
