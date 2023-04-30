// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <cmath>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/iterator_pack.h>
#include <vespa/searchlib/attribute/iterator_pack.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <vespa/searchlib/attribute/i_document_weight_attribute.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search::queryeval::wand {

//-----------------------------------------------------------------------------

struct Term;
using Terms = std::vector<Term>;
using score_t = int64_t;
using docid_t = uint32_t;
using ref_t = uint16_t;

using Attr = IDocumentWeightAttribute;
using AttrDictEntry = Attr::LookupResult;
using AttrDictEntries = std::vector<AttrDictEntry>;

//-----------------------------------------------------------------------------

/**
 * Wrapper used to specify underlying terms during setup
 **/
struct Term {
    // TODO: use unique_ptr for ownership
    SearchIterator          *search;
    int32_t                  weight;
    uint32_t                 estHits;
    fef::TermFieldMatchData *matchData;
    score_t                  maxScore = 0.0; // <- only used by rise wand test
    Term(SearchIterator *s, int32_t w, uint32_t e, fef::TermFieldMatchData *tfmd) noexcept
        : search(s), weight(w), estHits(e), matchData(tfmd)
    {}
    Term() noexcept : Term(nullptr, 0, 0, nullptr){}
    Term(SearchIterator *s, int32_t w, uint32_t e) noexcept : Term(s, w, e, nullptr) {}
    Term(SearchIterator::UP s, int32_t w, uint32_t e) noexcept : Term(s.release(), w, e, nullptr) {}
};

//-----------------------------------------------------------------------------

// input manipulation utilities

namespace {

struct Ident {
    template <typename T> T operator()(const T &t) const { return t; }
};

struct NumericOrder {
    size_t my_size;
    NumericOrder(size_t my_size_in) : my_size(my_size_in) {}
    size_t size() const { return my_size; }
    ref_t operator[](size_t idx) const { return idx; }
};

template <typename F, typename Order>
auto assemble(const F &f, const Order &order)->std::vector<decltype(f(0))> {
    std::vector<decltype(f(0))> result;
    result.reserve(order.size());
    for (size_t i = 0; i < order.size(); ++i) {
        result.push_back(f(order[i]));
    }
    return result;
}

int32_t get_max_weight(const SearchIterator &search) {
    const MinMaxPostingInfo *minMax = dynamic_cast<const MinMaxPostingInfo *>(search.getPostingInfo());
    return (minMax != nullptr) ? minMax->getMaxWeight() : std::numeric_limits<int32_t>::max();
}

} // namespace search::wand::<unnamed>

struct TermInput {
    const Terms &terms;
    TermInput(const Terms &terms_in) : terms(terms_in) {}
    size_t size() const { return terms.size(); }
    int32_t get_weight(ref_t ref) const { return terms[ref].weight; }
    uint32_t get_est_hits(ref_t ref) const { return terms[ref].estHits; }
    int32_t get_max_weight(ref_t ref) const { return ::search::queryeval::wand::get_max_weight(*(terms[ref].search)); }
    docid_t get_initial_docid(ref_t ref) const { return terms[ref].search->getDocId(); }
};

struct AttrInput {
    const std::vector<int32_t> &weights;
    const std::vector<IDocumentWeightAttribute::LookupResult> &dict_entries;
    AttrInput(const std::vector<int32_t> &weights_in,
              const std::vector<IDocumentWeightAttribute::LookupResult> &dict_entries_in)
        : weights(weights_in), dict_entries(dict_entries_in) {}
    size_t size() const { return weights.size(); }
    int32_t get_weight(ref_t ref) const { return weights[ref]; }
    uint32_t get_est_hits(ref_t ref) const { return dict_entries[ref].posting_size; }
    int32_t get_max_weight(ref_t ref) const { return dict_entries[ref].max_weight; }
    docid_t get_initial_docid(ref_t) const { return SearchIterator::beginId(); }
};

template <typename Input>
struct MaxSkipOrder {
    double estNumDocs;
    const Input &input;
    const std::vector<score_t> &max_score;
    MaxSkipOrder(docid_t docIdLimit, const Input &input_in,
                 const std::vector<score_t> &max_score_in)
        : estNumDocs(1.0), input(input_in), max_score(max_score_in)
    {
        estNumDocs = std::max(estNumDocs, docIdLimit - 1.0);
        for (size_t i = 0; i < input.size(); ++i) {
            estNumDocs = std::max(estNumDocs, (double)input.get_est_hits(i));
        }
    }
    double p_not_hit(double estHits) const {
        return ((estNumDocs - estHits) / (estNumDocs));
    }
    bool operator()(ref_t a, ref_t b) const {
        return ((p_not_hit(input.get_est_hits(a)) * max_score[a]) > (p_not_hit(input.get_est_hits(b)) * max_score[b]));
    }
};

//-----------------------------------------------------------------------------

namespace {

template <typename ITR, typename F>
vespalib::string do_stringify(const vespalib::string &title, ITR begin, ITR end, const F &f) {
    vespalib::string result = vespalib::make_string("[%s]{", title.c_str());
    for (ITR pos = begin; pos != end; ++pos) {
        if (pos != begin) {
            result.append(", ");
        }
        result.append(f(*pos));
    }
    result.append("}");
    return result;
}

} // namespace searchlib::wand::<unnamed>

//-----------------------------------------------------------------------------

template <typename IteratorPack>
class VectorizedState
{
private:
    std::vector<docid_t> _docId;
    std::vector<int32_t> _weight;
    std::vector<score_t> _maxScore;
    IteratorPack         _iteratorPack;

public:
    VectorizedState();
    VectorizedState(VectorizedState &&) noexcept;
    VectorizedState & operator=(VectorizedState &&) noexcept;
    ~VectorizedState();

    template <typename Scorer, typename Input>
    std::vector<ref_t> init_state(const Input &input, uint32_t docIdLimit);

    docid_t *docId() { return &(_docId[0]); }
    const int32_t *weight() const { return &(_weight[0]); }
    const score_t *maxScore() const { return &(_maxScore[0]); }

    docid_t &docId(ref_t ref) { return _docId[ref]; }
    int32_t weight(ref_t ref) const { return _weight[ref]; }
    score_t maxScore(ref_t ref) const { return _maxScore[ref]; }

    size_t size() const { return _docId.size(); }
    IteratorPack &iteratorPack() { return _iteratorPack; }

    uint32_t seek(uint16_t ref, uint32_t docid) { return _iteratorPack.seek(ref, docid); }
    int32_t get_weight(uint16_t ref, uint32_t docid) { return _iteratorPack.get_weight(ref, docid); }

    vespalib::string stringify_docid() const;
};

template <typename IteratorPack>
VectorizedState<IteratorPack>::VectorizedState()
    : _docId(),
      _weight(),
      _maxScore(),
      _iteratorPack()
{}
template <typename IteratorPack>
VectorizedState<IteratorPack>::~VectorizedState() = default;

template <typename IteratorPack>
VectorizedState<IteratorPack>::VectorizedState(VectorizedState &&) noexcept = default;

template <typename IteratorPack>
VectorizedState<IteratorPack> &
VectorizedState<IteratorPack>::operator=(VectorizedState &&) noexcept = default;

template <typename IteratorPack>
template <typename Scorer, typename Input>
std::vector<ref_t>
VectorizedState<IteratorPack>::init_state(const Input &input, uint32_t docIdLimit) {
    std::vector<ref_t> order;
    std::vector<score_t> max_scores;
    order.reserve(input.size());
    max_scores.reserve(input.size());
    for (size_t i = 0; i < input.size(); ++i) {
        order.push_back(i);
        max_scores.push_back(Scorer::calculate_max_score(input, i));
    }
    std::sort(order.begin(), order.end(), MaxSkipOrder<Input>(docIdLimit, input, max_scores));
    _docId = assemble([&input](ref_t ref){ return input.get_initial_docid(ref); }, order);
    _weight = assemble([&input](ref_t ref){ return input.get_weight(ref); }, order);
    _maxScore = assemble([&max_scores](ref_t ref){ return max_scores[ref]; }, order);
    return order;
}

template <typename IteratorPack>
vespalib::string
VectorizedState<IteratorPack>::stringify_docid() const {
    auto range = assemble(Ident(), NumericOrder(_docId.size()));
    return do_stringify("state{docid}", range.begin(), range.end(),
                        [this](ref_t ref)
                        {
                            return vespalib::make_string("%u:%u/%u", ref, _docId[ref], _iteratorPack.get_docid(ref));
                        });
}

//-----------------------------------------------------------------------------

class VectorizedIteratorTerms : public VectorizedState<SearchIteratorPack>
{
private:
    Terms _terms; // TODO: want to get rid of this

public:
    template <typename Scorer>
    VectorizedIteratorTerms(const Terms &t, const Scorer &, uint32_t docIdLimit,
                            fef::MatchData::UP childrenMatchData);
    VectorizedIteratorTerms(VectorizedIteratorTerms &&) noexcept;
    VectorizedIteratorTerms & operator=(VectorizedIteratorTerms &&) noexcept;

    ~VectorizedIteratorTerms();
    void unpack(uint16_t ref, uint32_t docid) { iteratorPack().unpack(ref, docid); }
    void visit_members(vespalib::ObjectVisitor &visitor) const;
    const Terms &input_terms() const { return _terms; }
};

template <typename Scorer>
VectorizedIteratorTerms::VectorizedIteratorTerms(const Terms &t, const Scorer &, uint32_t docIdLimit,
                                                 fef::MatchData::UP childrenMatchData)
    : _terms()
{
    std::vector<ref_t> order = init_state<Scorer>(TermInput(t), docIdLimit);
    _terms = assemble([&t](ref_t ref){ return t[ref]; }, order);
    iteratorPack() = SearchIteratorPack(assemble([&t](ref_t ref){ return t[ref].search; }, order),
                                        assemble([&t](ref_t ref){ return t[ref].matchData; }, order),
                                        std::move(childrenMatchData));
}

//-----------------------------------------------------------------------------

struct VectorizedAttributeTerms : VectorizedState<AttributeIteratorPack> {
    template <typename Scorer>
    VectorizedAttributeTerms(const std::vector<int32_t> &weights,
                             const std::vector<IDocumentWeightAttribute::LookupResult> &dict_entries,
                             const IDocumentWeightAttribute &attr,
                             const Scorer &,
                             docid_t docIdLimit)
    {
        std::vector<ref_t> order = init_state<Scorer>(AttrInput(weights, dict_entries), docIdLimit);
        std::vector<DocumentWeightIterator> iterators;
        iterators.reserve(order.size());
        for (size_t i = 0; i < order.size(); ++i) {
            attr.create(dict_entries[order[i]].posting_idx, iterators);
            docId(i) = (iterators.back().valid()) ? iterators.back().getKey() : search::endDocId;
        }
        iteratorPack() = AttributeIteratorPack(std::move(iterators));
    }
    void visit_members(vespalib::ObjectVisitor &) const {}
};

//-----------------------------------------------------------------------------

/**
 * Comparator used on vectorized state to sort by increasing document
 * id
 **/
struct DocIdOrder {
    const docid_t *termPos;
    DocIdOrder(docid_t *pos) : termPos(pos) {}
    bool at_end(ref_t ref) const { return termPos[ref] == search::endDocId; }
    docid_t get_pos(ref_t ref) const { return termPos[ref]; }
    bool operator()(ref_t a, ref_t b) const {
        return (termPos[a] < termPos[b]);
    }
};

//-----------------------------------------------------------------------------

template <typename FutureHeap, typename PastHeap>
class DualHeap
{
private:
    DocIdOrder         _futureCmp;
    std::vector<ref_t> _space;
    ref_t             *_future;    // start of future heap
    ref_t             *_present;   // start of present array
    ref_t             *_past;      // start of past heap
    ref_t             *_trash;     // end of used data
    size_t             _size;

public:
    DualHeap(const DocIdOrder &futureCmp, size_t size);
    ~DualHeap();
    void init();
    bool has_future() const { return (_future != _present);}
    bool has_present() const { return (_present != _past);}
    bool has_past() const { return (_past != _trash);}
    ref_t future() const { return FutureHeap::front(_future, _present); }
    ref_t first_present() const { return *_present; }
    ref_t last_present() const { return *(_past - 1); }
    void swap_presents() { std::swap(*_present, *(_past - 1)); }
    void push_future() { FutureHeap::push(_future, ++_present, _futureCmp); }
    void pop_future() { FutureHeap::pop(_future, _present--, _futureCmp); }
    void push_past() { PastHeap::push(--_past, _trash, std::less<ref_t>()); }
    void pop_past() { PastHeap::pop(_past++, _trash, std::less<ref_t>()); }
    void pop_any_past() { _past++; }
    void discard_last_present() {
        memmove((_past - 1), _past,
                (_trash - _past) * sizeof(ref_t));
        --_past;
        --_trash;
    }
    ref_t *present_begin() const { return _present; }
    ref_t *present_end() const { return _past; }
    vespalib::string stringify() const;
};

template <typename FutureHeap, typename PastHeap>
DualHeap<FutureHeap, PastHeap>::DualHeap(const DocIdOrder &futureCmp, size_t size)
    : _futureCmp(futureCmp),
      _space(),
      _future(nullptr),
      _present(nullptr),
      _past(nullptr),
      _trash(nullptr),
      _size(size)
{
    FutureHeap::require_left_heap();
    PastHeap::require_right_heap();
    _space.reserve(size);
    init();
}

template <typename FutureHeap, typename PastHeap>
DualHeap<FutureHeap, PastHeap>::~DualHeap() { }

template <typename FutureHeap, typename PastHeap>
void
DualHeap<FutureHeap, PastHeap>::init() {
    _space.clear();
    _future = &(_space[0]);
    _present = _future;
    for (size_t i = 0; i < _size; ++i) {
        if (!_futureCmp.at_end(i)) {
            _space.push_back(i);
            FutureHeap::push(_future, ++_present, _futureCmp);
        }
    }
    _past = _present;
    _trash = _past;
    assert(_future == &(_space[0])); // space has not moved
}

template <typename FutureHeap, typename PastHeap>
vespalib::string
DualHeap<FutureHeap, PastHeap>::stringify() const {
    return "Heaps: "
           + do_stringify("future", _future, _present,
                          [this](ref_t ref){ return vespalib::make_string("%u@%u", ref, _futureCmp.get_pos(ref)); })
           + " " + do_stringify("present", _present, _past,
                                [this](ref_t ref){ return vespalib::make_string("%u@%u", ref, _futureCmp.get_pos(ref)); })
           + " " + do_stringify("past", _past, _trash,
                                [this](ref_t ref){ return vespalib::make_string("%u@%u", ref, _futureCmp.get_pos(ref)); });
}
//-----------------------------------------------------------------------------

#define TermFrequencyScorer_TERM_SCORE_FACTOR 1000000.0

/**
 * Scorer used with WeakAndAlgorithm that calculates a pseudo term frequency
 * as max score and regular score for a term.
 */
struct TermFrequencyScorer
{
    // weight * idf, scaled to fixedpoint
    static score_t calculateMaxScore(double estHits, double weight) {
        return (score_t) (TermFrequencyScorer_TERM_SCORE_FACTOR * weight / (1.0 + log(1.0 + (estHits / 1000.0))));
    }

    static score_t calculateMaxScore(const Term &term) {
        return calculateMaxScore(term.estHits, term.weight) + 1;
    }

    template <typename Input>
    static score_t calculate_max_score(const Input &input, ref_t ref) {
        return calculateMaxScore(input.get_est_hits(ref), input.get_weight(ref)) + 1;
    }
};

//-----------------------------------------------------------------------------

/**
 * Scorer used with WeakAndAlgorithm that calculates a real dot product upper
 * bound as max score and dot product component score per term.
 */
struct DotProductScorer
{
    static score_t calculateMaxScore(const Term &term) {
        int32_t maxWeight = std::numeric_limits<int32_t>::max();
        const PostingInfo *postingInfo = term.search->getPostingInfo();
        if (postingInfo != NULL) {
            const MinMaxPostingInfo *minMax = dynamic_cast<const MinMaxPostingInfo *>(postingInfo);
            if (minMax != NULL) {
                maxWeight = minMax->getMaxWeight();
            }
        }
        return (score_t)term.weight * maxWeight;
    }

    template <typename Input>
    static score_t calculate_max_score(const Input &input, ref_t ref) {
        return input.get_weight(ref) * (score_t) input.get_max_weight(ref);
    }

    static score_t calculateScore(const Term &term, docid_t docId) {
        term.search->doUnpack(docId);
        return (score_t)term.weight * term.matchData->getWeight();
    }

    template <typename VectorizedTerms>
    static score_t calculateScore(VectorizedTerms &terms, ref_t ref, docid_t docId) {
        return terms.weight(ref) * (score_t)terms.get_weight(ref, docId);
    }
};

//-----------------------------------------------------------------------------

// used with parallel wand where we can safely discard hits based on score
struct GreaterThan {
    score_t threshold;
    GreaterThan(score_t t) : threshold(t) {}
    bool operator()(score_t score) const { return (score > threshold); }
};

// used with old-style vespa wand to ensure at least AND'ish results
struct GreaterThanEqual {
    score_t threshold;
    GreaterThanEqual(score_t t) : threshold(t) {}
    bool operator()(score_t score) const { return (score >= threshold); }
};

//-----------------------------------------------------------------------------

class Algorithm
{
private:
    docid_t _candidate;
    score_t _upperBound;
    score_t _maxUpperBound;
    score_t _partial_score;

    template <typename VectorizedTerms>
    bool step_term(VectorizedTerms &terms, ref_t ref) {
        terms.docId(ref) = terms.seek(ref, _candidate);
        return (terms.docId(ref) == _candidate);
    }

    template <typename VectorizedTerms, typename Heaps>
    void evict_last_present(VectorizedTerms &terms, Heaps &heaps) {
        _maxUpperBound -= terms.maxScore(heaps.last_present());
        if (terms.docId(heaps.last_present()) != search::endDocId) {
            heaps.swap_presents();
            heaps.push_future();
        } else {
            heaps.discard_last_present();
        }
    }

    template <typename Heaps>
    void discard_candidate(Heaps &heaps) {
        while (heaps.has_present()) {
            heaps.push_past();
        }
        _upperBound = 0;
    }

    template <typename VectorizedTerms, typename Heaps>
    void step_optimal_term(VectorizedTerms &terms, Heaps &heaps) {
        heaps.pop_past();
        if (step_term(terms, heaps.last_present())) {
            _upperBound += terms.maxScore(heaps.last_present());
        } else {
            evict_last_present(terms, heaps);
        }
    }

    template <typename VectorizedTerms, typename Heaps>
    void step_candidate(VectorizedTerms &terms, Heaps &heaps) {
        discard_candidate(heaps); // will reset upper bound
        _candidate = terms.docId(heaps.future());
        do {
            heaps.pop_future();
            _upperBound += terms.maxScore(heaps.first_present());
        } while (heaps.has_future() && terms.docId(heaps.future()) == _candidate);
        _maxUpperBound += _upperBound;
    }

    template <typename VectorizedTerms, typename Heaps, typename Scorer, typename AboveThreshold>
    bool check_present_score(VectorizedTerms &terms, Heaps &heaps, score_t &max_score, const Scorer &, AboveThreshold &&aboveThreshold) {
        ref_t *end = heaps.present_end();
        for (ref_t *ref = heaps.present_begin(); ref != end; ++ref) {
            score_t term_score = Scorer::calculateScore(terms, *ref, _candidate);
            _partial_score += term_score;
            max_score -= (terms.maxScore(*ref) - term_score);
            if (!aboveThreshold(max_score)) {
                return false;
            }
        }
        return true;
    }

    template <typename VectorizedTerms, typename Heaps, typename Scorer, typename AboveThreshold>
    bool check_past_score(VectorizedTerms &terms, Heaps &heaps, score_t &max_score, const Scorer &, AboveThreshold &&aboveThreshold) {
        while (heaps.has_past() && !aboveThreshold(_partial_score)) {
            heaps.pop_past();
            if (step_term(terms, heaps.last_present())) {
                score_t term_score = Scorer::calculateScore(terms, heaps.last_present(), _candidate);
                _partial_score += term_score;
                max_score -= (terms.maxScore(heaps.last_present()) - term_score);
            } else {
                max_score -= terms.maxScore(heaps.last_present());
                evict_last_present(terms, heaps);
            }
            if (!aboveThreshold(max_score)) {
                return false;
            }
        }
        return true;
    }

    void reset() {
        _candidate = SearchIterator::beginId();
        _upperBound = 0;
        _maxUpperBound = 0;
        _partial_score = 0;
    }

public:
    Algorithm() : _candidate(SearchIterator::beginId()), _upperBound(0), _maxUpperBound(0), _partial_score(0) {}

    template <typename VectorizedTerms, typename Heaps>
    void init_range(VectorizedTerms &terms, Heaps &heaps, uint32_t begin_id, uint32_t end_id) {
        reset();
        terms.iteratorPack().initRange(begin_id, end_id);
        for (size_t i = 0; i < terms.size(); ++i) {
            terms.docId(i) = terms.iteratorPack().get_docid(i);
        }
        heaps.init();
    }

    docid_t get_candidate() const { return _candidate; }
    score_t get_upper_bound() const { return _upperBound; }

    template <typename VectorizedTerms, typename Heaps>
    void set_candidate(VectorizedTerms &terms, Heaps &heaps, docid_t candidate) {
        _candidate = candidate;
        while (heaps.has_future() && terms.docId(heaps.future()) < candidate) {
            heaps.pop_future();
            _maxUpperBound += terms.maxScore(heaps.first_present());
        }
        discard_candidate(heaps); // will reset upper bound
        while (heaps.has_future() && terms.docId(heaps.future()) == candidate) {
            heaps.pop_future();
            _upperBound += terms.maxScore(heaps.first_present());
        }
        _maxUpperBound += _upperBound;
    }

    template <typename VectorizedTerms, typename Heaps, typename AboveThreshold>
    bool solve_wand_constraint(VectorizedTerms &terms, Heaps &heaps, AboveThreshold &&aboveThreshold) {
        while (!aboveThreshold(_upperBound)) {
            if (aboveThreshold(_maxUpperBound)) {
                step_optimal_term(terms, heaps);
            } else if (heaps.has_future()) {
                step_candidate(terms, heaps);
            } else {
                return false;
            }
        }
        return true;
    }

    template <typename VectorizedTerms, typename Heaps, typename AboveThreshold>
    bool check_wand_constraint(VectorizedTerms &terms, Heaps &heaps, AboveThreshold &&aboveThreshold) {
        while (!aboveThreshold(_upperBound)) {
            if (aboveThreshold(_maxUpperBound)) {
                step_optimal_term(terms, heaps);
            } else {
                return false;
            }
        }
        return true;
    }

    template <typename VectorizedTerms, typename Heaps, typename Scorer, typename AboveThreshold>
    bool check_score(VectorizedTerms &terms, Heaps &heaps, Scorer &&scorer, AboveThreshold &&aboveThreshold) {
        _partial_score = 0;
        score_t max_score = _maxUpperBound;
        if (check_present_score(terms, heaps, max_score, scorer, aboveThreshold)) {
            if (check_past_score(terms, heaps, max_score, scorer, aboveThreshold)) {
                return aboveThreshold(_partial_score);
            }
        }
        return false;
    }

    template <typename VectorizedTerms, typename Heaps, typename Scorer>
    score_t get_full_score(VectorizedTerms &terms, Heaps &heaps, Scorer &&) {
        score_t score = _partial_score;
        while (heaps.has_past()) {
            heaps.pop_any_past();
            if (step_term(terms, heaps.last_present())) {
                score += Scorer::calculateScore(terms, heaps.last_present(), _candidate);
            } else {
                evict_last_present(terms, heaps);
            }
        }
        return score;
    }

    template <typename VectorizedTerms, typename Heaps>
    void find_matching_terms(VectorizedTerms &terms, Heaps &heaps) {
        while (heaps.has_past()) {
            heaps.pop_any_past();
            if (step_term(terms, heaps.last_present())) {
                _upperBound += terms.maxScore(heaps.last_present());
            } else {
                evict_last_present(terms, heaps);
            }
        }
    }
};

//-----------------------------------------------------------------------------

}

//-----------------------------------------------------------------------------

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::wand::Term &obj);

//-----------------------------------------------------------------------------

