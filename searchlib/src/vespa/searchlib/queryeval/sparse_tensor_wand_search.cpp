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
#include <cmath>
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
 * The docid posting lists of the query labels, as terms for wand::Algorithm.
 * The score of a term is its contribution to the dot product (query cell * posting weight),
 * and its max score is an upper bound of that, calculated from the min/max posting weight.
 * Terms are ordered like wand::MaxSkipOrder, so the most valuable terms are checked first.
 *
 * Scores are converted to scaled 64-bit integers, so the sums done by the WAND algorithm are exact:
 * - The finite part of the score range (sum of finite positive upper bounds, minus sum of finite
 *   negative lower bounds) is scaled to span score_range. Term scores and max scores are rounded up.
 * - Converted thresholds are clamped to max_threshold, which is above any sum of finite term scores.
 * - A term with an unbounded (infinite or NaN) upper bound always scores unbounded_score, so a document
 *   matching it passes any converted threshold: the other term scores sum to at least -score_range.
 * - Negative contributions from a term with an unbounded lower bound are raised to 0.
 * Raising a term score never loses hits; the exact score of the candidates decides anyway.
 * With at most 2^16 terms, the sums stay below 2^16 * unbounded_score = 2^62.
 */
class PostingTerms {
    std::vector<uint32_t>                     _docid; // current docid of each term (as seen by the algorithm)
    std::vector<int64_t>                      _min_score;
    std::vector<int64_t>                      _max_score;
    std::vector<double>                       _query_weight;
    std::vector<DocidWithFloatWeightIterator> _children;
    double                                    _scale; // conversion factor from double to integer scores
    int64_t                                   _slack; // integer slack for float posting weight rounding

    static constexpr double  score_range = 0x1.0p44;
    static constexpr double  max_threshold = 2 * score_range;
    static constexpr int64_t unbounded_score = static_cast<int64_t>(4 * score_range);
    // 1e-6 of score_range (rounded up), large enough to cover float rounding of the posting weights
    static constexpr int64_t rounding_slack = static_cast<int64_t>(score_range * 1e-6) + 1;
    static_assert(SparseTensorWandSearch::max_postings * double(unbounded_score) <= 0x1.0p62);

public:
    PostingTerms(std::vector<DocidWithFloatWeightIterator> postings, const std::vector<double>& query_weights,
                 uint32_t docid_limit);
    PostingTerms(const PostingTerms&) = delete;
    PostingTerms& operator=(const PostingTerms&) = delete;
    ~PostingTerms();

    // interface used by wand::Algorithm and wand::DualHeap
    uint32_t* docId() noexcept { return _docid.data(); }
    uint32_t& docId(wand::ref_t ref) noexcept { return _docid[ref]; }
    int64_t maxScore(wand::ref_t ref) const noexcept { return _max_score[ref]; }
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

    // Integer bound used by the WAND checks for the given (non-negative) score threshold.
    // Term scores and max scores are rounded up, so the integer sums are never below the
    // scaled posting score sum. The posting weights are float (possibly rounded from double
    // tensor cells), and the exact score is summed in a different order, so the bound is
    // lowered by a small slack to avoid losing hits close to the threshold.
    // The threshold may be far above the max score (initial score threshold from the query,
    // or max double when not tracking any hits), so it is clamped to max_threshold; only
    // documents matching a term with an unbounded upper bound pass then.
    int64_t converted_threshold(double threshold) const noexcept {
        double scaled = threshold * _scale;
        if (!(scaled < max_threshold)) { // also catches NaN
            scaled = max_threshold;
        }
        if (!(scaled > 0.0)) {
            return 0;
        }
        int64_t cvt = static_cast<int64_t>(std::floor(scaled));
        return std::max(cvt - _slack, int64_t(0));
    }

    // contribution to the dot product from the given term (positioned at the current candidate),
    // scaled, rounded up and clamped to [min score, max score] of the term. A NaN contribution
    // gives the min score; the exact score is then also NaN, which is never a hit.
    int64_t term_score(wand::ref_t ref) const noexcept {
        double scaled = std::ceil(_query_weight[ref] * _children[ref].getData() * _scale);
        if (!(scaled > static_cast<double>(_min_score[ref]))) { // also catches NaN
            return _min_score[ref];
        }
        if (!(scaled < static_cast<double>(_max_score[ref]))) {
            return _max_score[ref];
        }
        return static_cast<int64_t>(scaled);
    }
};

/**
 * Scorer for wand::Algorithm using the weights in the posting lists.
 */
struct PostingScorer {
    static int64_t calculateScore(const PostingTerms& terms, wand::ref_t ref, uint32_t) noexcept {
        return terms.term_score(ref);
    }
};

PostingTerms::PostingTerms(std::vector<DocidWithFloatWeightIterator> postings,
                           const std::vector<double>& query_weights, uint32_t docid_limit)
    : _docid(), _min_score(), _max_score(), _query_weight(), _children(), _scale(1.0), _slack(0) {
    assert(query_weights.size() == postings.size());
    static_assert(SparseTensorWandSearch::max_postings == std::numeric_limits<wand::ref_t>::max());
    assert(postings.size() <= SparseTensorWandSearch::max_postings);
    constexpr double    inf = std::numeric_limits<double>::infinity();
    size_t              num_terms = postings.size();
    std::vector<double> upper_bounds, lower_bounds, finite_max_scores, finite_negated_mins;
    std::vector<double> est_hits;
    upper_bounds.reserve(num_terms);
    lower_bounds.reserve(num_terms);
    finite_max_scores.reserve(num_terms);
    finite_negated_mins.reserve(num_terms);
    est_hits.reserve(num_terms);
    double est_num_docs = std::max(1.0, docid_limit - 1.0);
    double max_finite_bound = 0.0;
    // posting lists are always btrees here, so the min/max weight aggregates are available
    for (uint32_t ref = 0; ref < num_terms; ++ref) {
        const auto& aggr = postings[ref].getAggregated();
        double      qw = query_weights[ref];
        // the largest and smallest qw * w for any document weight w in [min, max]
        double upper_bound = (qw >= 0.0) ? qw * aggr.getMax() : qw * aggr.getMin();
        double lower_bound = (qw >= 0.0) ? qw * aggr.getMin() : qw * aggr.getMax();
        upper_bounds.push_back(upper_bound);
        lower_bounds.push_back(lower_bound);
        // a term that can only lower the score never helps a document pass the threshold
        double max_score = (upper_bound < inf) ? std::max(upper_bound, 0.0) : 0.0;
        double negated_min = (lower_bound > -inf) ? std::max(-lower_bound, 0.0) : 0.0;
        finite_max_scores.push_back(max_score);
        finite_negated_mins.push_back(negated_min);
        max_finite_bound = std::max({max_finite_bound, max_score, negated_min});
        size_t size = postings[ref].size();
        est_hits.push_back(size);
        est_num_docs = std::max(est_num_docs, double(size));
    }
    // a document can match any subset of the terms, so only positive contributions raise the max score
    // and only negative contributions lower the min score.
    double range = huffman_sum_positive(finite_max_scores) + huffman_sum_positive(finite_negated_mins);
    // map the finite score range onto score_range. if all finite contributions are zero (range is 0)
    // any scale works; keep 1.0 and no slack. if the range overflows, scale each finite bound to at most
    // score_range / num_terms instead. if the range is so small that the scale overflows, use the largest
    // finite scale; the scaled bounds are then still below score_range.
    if (range > 0.0) {
        if (std::isfinite(range)) {
            _scale = std::min(score_range / range, std::numeric_limits<double>::max());
        } else {
            _scale = (score_range / num_terms) / max_finite_bound;
        }
        _slack = rounding_slack;
    }
    std::vector<int64_t> min_scores, max_scores;
    min_scores.reserve(num_terms);
    max_scores.reserve(num_terms);
    for (uint32_t ref = 0; ref < num_terms; ++ref) {
        if (upper_bounds[ref] < inf) {
            max_scores.push_back(static_cast<int64_t>(std::ceil(finite_max_scores[ref] * _scale)));
            min_scores.push_back((lower_bounds[ref] > -inf)
                                     ? static_cast<int64_t>(std::ceil(lower_bounds[ref] * _scale))
                                     : int64_t(0));
        } else {
            max_scores.push_back(unbounded_score);
            min_scores.push_back(unbounded_score);
        }
    }
    std::vector<uint32_t> order(num_terms);
    for (uint32_t ref = 0; ref < order.size(); ++ref) {
        order[ref] = ref;
    }
    auto p_not_hit = [&](double estHits) noexcept { return ((est_num_docs - estHits) / (est_num_docs)); };
    auto skip_value = [&](uint32_t ref) noexcept { return p_not_hit(est_hits[ref]) * double(max_scores[ref]); };
    std::stable_sort(order.begin(), order.end(),
                     [&](uint32_t a, uint32_t b) { return skip_value(a) > skip_value(b); });
    _docid.reserve(num_terms);
    _min_score.reserve(num_terms);
    _max_score.reserve(num_terms);
    _query_weight.reserve(num_terms);
    _children.reserve(num_terms);
    for (uint32_t ref : order) {
        _children.push_back(std::move(postings[ref]));
        _min_score.push_back(min_scores[ref]);
        _max_score.push_back(max_scores[ref]);
        _query_weight.push_back(query_weights[ref]);
        _docid.push_back(get_docid(_children.size() - 1));
    }
}

PostingTerms::~PostingTerms() = default;

/**
 * Strict iterator using the WAND algorithm (see wand::Algorithm) on the posting lists of the query labels,
 * like the parallel WAND iterator does. Candidates are found using an upper bound of the contribution from
 * each posting list, and checked using the weights in the posting lists (with early exit when the remaining
 * upper bounds cannot reach the threshold). Only candidates passing that check are scored using the document
 * tensor, and that exact score decides whether the candidate is a hit.
 *
 * The posting scores are converted to scaled 64-bit integers (see PostingTerms), so the sums done by the
 * WAND algorithm are exact. Since the float posting weights may differ slightly from the exact score,
 * the checks are done against the threshold lowered by a small slack (see PostingTerms::converted_threshold).
 */
template <typename Scorer, typename FutureHeap, typename PastHeap>
class SparseTensorWandPostingSearch final : public SparseTensorWandSearch {
    using Heaps = wand::DualHeap<FutureHeap, PastHeap>;

    fef::TermFieldMatchData& _tfmd;
    Scorer                   _scorer;
    PostingTerms             _terms;
    Heaps                    _heaps;
    wand::Algorithm          _algo;
    const MatchParams        _match_params;
    std::vector<double>      _local_scores;
    const bool               _readonly_scores_heap;
    double                   _last_score;

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
        int64_t bound = _terms.converted_threshold(threshold);
        auto    above_bound = [bound](int64_t upper_bound) noexcept { return upper_bound > bound; };
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
