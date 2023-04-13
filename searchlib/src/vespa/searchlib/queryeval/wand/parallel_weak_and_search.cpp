// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parallel_weak_and_search.h"
#include <vespa/searchlib/queryeval/document_weight_search_iterator.h>
#include <vespa/searchlib/queryeval/monitoring_dump_iterator.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/vespalib/objects/visit.h>

#include <vespa/log/log.h>
LOG_SETUP(".queryeval.parallel_weak_and_search");

using vespalib::make_string;

namespace search::queryeval {

using MatchParams = ParallelWeakAndSearch::MatchParams;
using RankParams = ParallelWeakAndSearch::RankParams;

namespace wand {

namespace { bool should_monitor_wand() { return LOG_WOULD_LOG(spam); } }


template <typename VectorizedTerms, typename FutureHeap, typename PastHeap, bool IS_STRICT>
class ParallelWeakAndSearchImpl : public ParallelWeakAndSearch
{
private:
    fef::TermFieldMatchData       &_tfmd;
    VectorizedTerms                _terms;
    DualHeap<FutureHeap, PastHeap> _heaps;
    Algorithm                      _algo;
    score_t                        _threshold;
    score_t                        _boostedThreshold;
    const MatchParams              _matchParams;
    std::vector<score_t>           _localScores;

    void updateThreshold(score_t newThreshold) {
        if (newThreshold > _threshold) {
            _threshold = newThreshold;
            _boostedThreshold = (newThreshold * _matchParams.thresholdBoostFactor);
        }
    }

    void seek_strict(uint32_t docid) {
        _algo.set_candidate(_terms, _heaps, docid);
        while (_algo.solve_wand_constraint(_terms, _heaps, GreaterThan(_boostedThreshold))) {
            if (_algo.check_score(_terms, _heaps, DotProductScorer(), GreaterThan(_threshold))) {
                setDocId(_algo.get_candidate());
                return;
            } else {
                _algo.set_candidate(_terms, _heaps, _algo.get_candidate() + 1);
            }
        }
        setAtEnd();
    }

    void seek_unstrict(uint32_t docid) {
        if (docid > _algo.get_candidate()) {
            _algo.set_candidate(_terms, _heaps, docid);
            if (_algo.check_wand_constraint(_terms, _heaps, GreaterThan(_boostedThreshold))) {
                if (_algo.check_score(_terms, _heaps, DotProductScorer(), GreaterThan(_threshold))) {
                    setDocId(_algo.get_candidate());
                }
            }
        }
    }

public:
    ParallelWeakAndSearchImpl(fef::TermFieldMatchData &tfmd,
                              VectorizedTerms &&terms,
                              const MatchParams &matchParams)
        : _tfmd(tfmd),
          _terms(std::move(terms)),
          _heaps(DocIdOrder(_terms.docId()), _terms.size()),
          _algo(),
          _threshold(matchParams.scoreThreshold),
          _boostedThreshold(_threshold * matchParams.thresholdBoostFactor),
          _matchParams(matchParams),
          _localScores()
    {
    }
    virtual size_t get_num_terms() const override { return _terms.size(); }
    virtual int32_t get_term_weight(size_t idx) const override { return _terms.weight(idx); }
    virtual score_t get_max_score(size_t idx) const override { return _terms.maxScore(idx); }
    virtual const MatchParams &getMatchParams() const override { return _matchParams; }

    virtual void doSeek(uint32_t docid) override {
        updateThreshold(_matchParams.scores.getMinScore());
        if (IS_STRICT) {
            seek_strict(docid);
        } else {
            seek_unstrict(docid);
        }
    }
    virtual void doUnpack(uint32_t docid) override {
        score_t score = _algo.get_full_score(_terms, _heaps, DotProductScorer());
        _localScores.push_back(score);
        if (_localScores.size() == _matchParams.scoresAdjustFrequency) {
            _matchParams.scores.adjust(&_localScores[0], &_localScores[0] + _localScores.size());
            _localScores.clear();
        }
        _tfmd.setRawScore(docid, score);
    }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const override {
        _terms.visit_members(visitor);
    }
    void initRange(uint32_t begin, uint32_t end) override {
        ParallelWeakAndSearch::initRange(begin, end);
        _algo.init_range(_terms, _heaps, begin, end);
    }
    Trinary is_strict() const override { return IS_STRICT ? Trinary::True : Trinary::False; }
};

namespace {

wand::Terms
insertMonitoringSearchIterator(const wand::Terms &terms)
{
    wand::Terms retval = terms;
    for (size_t i = 0; i < terms.size(); ++i) {
        wand::Term &t = retval[i];
        t.search = new MonitoringSearchIterator
            (make_string("w%d:e%u:m%" PRId64 "",
                         t.weight, t.estHits, DotProductScorer::calculateMaxScore(t)),
             SearchIterator::UP(t.search), true);
    }
    return retval;
}

template <typename FutureHeap, typename PastHeap, bool IS_STRICT>
SearchIterator::UP
createWand(const wand::Terms &terms,
           const ParallelWeakAndSearch::MatchParams &matchParams,
           ParallelWeakAndSearch::RankParams &&rankParams)
{
    using WandType = ParallelWeakAndSearchImpl<VectorizedIteratorTerms, FutureHeap, PastHeap, IS_STRICT>;
    if (should_monitor_wand()) {
        wand::Terms termsWithMonitoring = insertMonitoringSearchIterator(terms);
        auto monitoringIterator = std::make_unique<MonitoringSearchIterator>(
                make_string("PWAND(%u,%" PRId64 "),strict=%u",
                            matchParams.scores.getScoresToTrack(),
                            matchParams.scoreThreshold, IS_STRICT),
                std::make_unique<WandType>(rankParams.rootMatchData,
                                           VectorizedIteratorTerms(termsWithMonitoring,
                                                                   DotProductScorer(),
                                                                   matchParams.docIdLimit,
                                                                   std::move(rankParams.childrenMatchData)),
                                           matchParams),
                false);
        return std::make_unique<MonitoringDumpIterator>(std::move(monitoringIterator));
    }
    return std::make_unique<WandType>(rankParams.rootMatchData,
                                      VectorizedIteratorTerms(terms,
                                                              DotProductScorer(),
                                                              matchParams.docIdLimit,
                                                              std::move(rankParams.childrenMatchData)),
                                      matchParams);
}

} // namespace search::queryeval::wand::<unnamed>

} // namespace search::queryeval::wand

SearchIterator::UP
ParallelWeakAndSearch::createArrayWand(const Terms &terms,
                                       const MatchParams &matchParams,
                                       RankParams &&rankParams,
                                       bool strict)
{
    if (strict) {
        return wand::createWand<vespalib::LeftArrayHeap, vespalib::RightArrayHeap, true>(terms, matchParams, std::move(rankParams));
    } else {
        return wand::createWand<vespalib::LeftArrayHeap, vespalib::RightArrayHeap, false>(terms, matchParams, std::move(rankParams));
    }
}

SearchIterator::UP
ParallelWeakAndSearch::createHeapWand(const Terms &terms,
                                      const MatchParams &matchParams,
                                      RankParams &&rankParams,
                                      bool strict)
{
    if (strict) {
        return wand::createWand<vespalib::LeftHeap, vespalib::RightHeap, true>(terms, matchParams, std::move(rankParams));
    } else {
        return wand::createWand<vespalib::LeftHeap, vespalib::RightHeap, false>(terms, matchParams, std::move(rankParams));
    }
}

SearchIterator::UP
ParallelWeakAndSearch::create(const Terms &terms,
                              const MatchParams &matchParams,
                              RankParams &&rankParams,
                              bool strict)
{
    if (terms.size() < 128) {
        return createArrayWand(terms, matchParams, std::move(rankParams), strict);
    } else {
        return createHeapWand(terms, matchParams, std::move(rankParams), strict);
    }
}

//-----------------------------------------------------------------------------

namespace {

template <typename VectorizedTerms, typename FutureHeap, typename PastHeap>
SearchIterator::UP create_helper(search::fef::TermFieldMatchData &tfmd, VectorizedTerms &&terms, const MatchParams &params, bool strict) {
    return (strict)
        ? SearchIterator::UP(new wand::ParallelWeakAndSearchImpl<VectorizedTerms, FutureHeap, PastHeap, true>(tfmd, std::move(terms), params))
        : SearchIterator::UP( new wand::ParallelWeakAndSearchImpl<VectorizedTerms, FutureHeap, PastHeap, false>(tfmd, std::move(terms), params));
}

template <typename VectorizedTerms>
SearchIterator::UP create_helper(search::fef::TermFieldMatchData &tfmd, VectorizedTerms &&terms, const MatchParams &params, bool strict, bool use_array) {
    return (use_array)
        ? create_helper<VectorizedTerms, vespalib::LeftArrayHeap, vespalib::RightArrayHeap>(tfmd, std::move(terms), params, strict)
        : create_helper<VectorizedTerms, vespalib::LeftHeap, vespalib::RightHeap>(tfmd, std::move(terms), params, strict);
}

} // namespace search::queryeval::<unnamed>

SearchIterator::UP
ParallelWeakAndSearch::create(search::fef::TermFieldMatchData &tfmd,
                              const MatchParams &matchParams,
                              const std::vector<int32_t> &weights,
                              const std::vector<IDocumentWeightAttribute::LookupResult> &dict_entries,
                              const IDocumentWeightAttribute &attr,
                              bool strict)
{
    assert(weights.size() == dict_entries.size());
    if (!wand::should_monitor_wand()) {
        wand::VectorizedAttributeTerms terms(weights, dict_entries, attr, wand::DotProductScorer(), matchParams.docIdLimit);
        return create_helper(tfmd, std::move(terms), matchParams, strict, (weights.size() < 128));
    } else {
        // reverse-wrap direct iterators into old API to be compatible with monitoring
        fef::MatchDataLayout layout;
        std::vector<fef::TermFieldHandle> handles;
        for (size_t i = 0; i < weights.size(); ++i) {
            handles.push_back(layout.allocTermField(tfmd.getFieldId()));
        }
        fef::MatchData::UP childrenMatchData = layout.createMatchData();
        assert(childrenMatchData->getNumTermFields() == dict_entries.size());
        wand::Terms terms;
        for (size_t i = 0; i < dict_entries.size(); ++i) {
            terms.push_back(wand::Term(new DocumentWeightSearchIterator(*(childrenMatchData->resolveTermField(handles[i])), attr, dict_entries[i]),
                                       weights[i],
                                       dict_entries[i].posting_size,
                                       childrenMatchData->resolveTermField(handles[i])));
        }
        assert(terms.size() == dict_entries.size());
        return create(terms, matchParams, RankParams(tfmd, std::move(childrenMatchData)), strict);
    }
}

}
