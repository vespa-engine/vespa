// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weak_and_search.h"
#include "wand_parts.h"
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/vespalib/util/left_right_heap.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <vespa/vespalib/objects/visit.hpp>

namespace search {
namespace queryeval {
namespace wand {

template <typename FutureHeap, typename PastHeap, bool IS_STRICT>
class WeakAndSearchLR : public WeakAndSearch
{
private:
    typedef vespalib::PriorityQueue<score_t> Scores;

    VectorizedIteratorTerms        _terms;
    DualHeap<FutureHeap, PastHeap> _heaps;
    Algorithm                      _algo;
    score_t                        _threshold; // current score threshold
    Scores                         _scores;    // best n scores
    const uint32_t                 _n;

    void seek_strict(uint32_t docid) {
        _algo.set_candidate(_terms, _heaps, docid);
        if (_algo.solve_wand_constraint(_terms, _heaps, GreaterThanEqual(_threshold))) {
            setDocId(_algo.get_candidate());
        } else {
            setAtEnd();
        }
    }

    void seek_unstrict(uint32_t docid) {
        if (docid > _algo.get_candidate()) {
            _algo.set_candidate(_terms, _heaps, docid);
            if (_algo.check_wand_constraint(_terms, _heaps, GreaterThanEqual(_threshold))) {
                setDocId(_algo.get_candidate());
            }
        }
    }

public:
    WeakAndSearchLR(const Terms &terms, uint32_t n)
        : _terms(terms,
                 TermFrequencyScorer(),
                 0,
                 fef::MatchData::UP(nullptr)),
          _heaps(DocIdOrder(_terms.docId()), _terms.size()),
          _algo(),
          _threshold(1),
          _scores(),
          _n(n)
    {
    }
    virtual size_t get_num_terms() const override { return _terms.size(); }
    virtual int32_t get_term_weight(size_t idx) const override { return _terms.weight(idx); }
    virtual score_t get_max_score(size_t idx) const override { return _terms.maxScore(idx); }
    const Terms &getTerms() const override { return _terms.input_terms(); }
    uint32_t getN() const override { return _n; }
    void doSeek(uint32_t docid) override {
        if (IS_STRICT) {
            seek_strict(docid);
        } else {
            seek_unstrict(docid);
        }
    }
    void doUnpack(uint32_t docid) override {
        _algo.find_matching_terms(_terms, _heaps);
        _scores.push(_algo.get_upper_bound());
        if (_scores.size() > _n) {
            _scores.pop_front();
        }
        if (_scores.size() == _n) {
            _threshold = _scores.front();
        }
        ref_t *end = _heaps.present_end();
        for (ref_t *ref = _heaps.present_begin(); ref != end; ++ref) {
            _terms.unpack(*ref, docid);
        }
    }
    void initRange(uint32_t begin, uint32_t end) override {
        WeakAndSearch::initRange(begin, end);
        _algo.init_range(_terms, _heaps, begin, end);
        if (_n == 0) {
            setAtEnd();
        }
    }
    Trinary is_strict() const override { return IS_STRICT ? Trinary::True : Trinary::False; }
};

//-----------------------------------------------------------------------------

} // namespace search::queryeval::wand

//-----------------------------------------------------------------------------

void
WeakAndSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "n",     getN());
    visit(visitor, "terms", getTerms());
}

//-----------------------------------------------------------------------------

SearchIterator::UP
WeakAndSearch::createArrayWand(const Terms &terms, uint32_t n, bool strict)
{
    if (strict) {
        return SearchIterator::UP(new wand::WeakAndSearchLR<vespalib::LeftArrayHeap, vespalib::RightArrayHeap, true>(terms, n));
    } else {
        return SearchIterator::UP(new wand::WeakAndSearchLR<vespalib::LeftArrayHeap, vespalib::RightArrayHeap, false>(terms, n));
    }
}

SearchIterator::UP
WeakAndSearch::createHeapWand(const Terms &terms, uint32_t n, bool strict)
{
    if (strict) {
        return SearchIterator::UP(new wand::WeakAndSearchLR<vespalib::LeftHeap, vespalib::RightHeap, true>(terms, n));
    } else {
        return SearchIterator::UP(new wand::WeakAndSearchLR<vespalib::LeftHeap, vespalib::RightHeap, false>(terms, n));
    }
}

SearchIterator::UP
WeakAndSearch::create(const Terms &terms, uint32_t n, bool strict)
{
    if (terms.size() < 128) {
        return createArrayWand(terms, n, strict);
    } else {
        return createHeapWand(terms, n, strict);
    }
}

//-----------------------------------------------------------------------------

}  // namespace queryeval
}  // namespace search
