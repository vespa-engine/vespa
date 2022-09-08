// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_phase_limiter.h"
#include <vespa/searchlib/queryeval/andsearchstrict.h>
#include <vespa/vespalib/data/slime/cursor.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.match_phase_limiter");

using search::queryeval::SearchIterator;
using search::queryeval::Searchable;
using search::queryeval::IRequestContext;

namespace proton::matching {

namespace {

template<bool PRE_FILTER>
class LimitedSearchT : public LimitedSearch {
public:
    LimitedSearchT(SearchIterator::UP limiter, SearchIterator::UP search) :
        LimitedSearch(std::move(PRE_FILTER ? limiter : search),
                      std::move(PRE_FILTER ? search : limiter))
    {
    }
    void doUnpack(uint32_t docId) override { 
        if (PRE_FILTER) {
            getSecond().doUnpack(docId);
        } else {
            getFirst().doUnpack(docId);
        }
    }
};

} // namespace proton::matching::<unnamed>

void
LimitedSearch::doSeek(uint32_t docId)
{
    
    uint32_t currentId(docId);
    for (; !isAtEnd(currentId); currentId++) {
        _first->seek(currentId);
        currentId = _first->getDocId();
        if (isAtEnd(currentId)) {
            break;
        }
        if (_second->seek(currentId)) {
            break;
        }
    }
    setDocId(currentId);
}

void
LimitedSearch::initRange(uint32_t begin, uint32_t end) {
    SearchIterator::initRange(begin, end);
    getFirst().initRange(begin, end);
    getSecond().initRange(begin, end);
}

void
LimitedSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "first", getFirst());
    visit(visitor, "second", getSecond());
}

MatchPhaseLimiter::MatchPhaseLimiter(uint32_t docIdLimit,
                                     const RangeQueryLocator & rangeQueryLocator,
                                     Searchable &searchable_attributes,
                                     IRequestContext & requestContext,
                                     const DegradationParams & degradation,
                                     const DiversityParams &diversity)
    : _postFilterMultiplier(degradation.post_filter_multiplier),
      _maxFilterCoverage(degradation.max_filter_coverage),
      _calculator(degradation.max_hits, diversity.min_groups, degradation.sample_percentage),
      _limiter_factory(rangeQueryLocator, searchable_attributes, requestContext,
                       degradation.attribute, degradation.descending,
                       diversity.attribute, diversity.cutoff_factor, diversity.cutoff_strategy),
      _coverage(docIdLimit)
{ }

MatchPhaseLimiter::~MatchPhaseLimiter() = default;

namespace {

template <bool PRE_FILTER>
SearchIterator::UP
do_limit(AttributeLimiter &limiter_factory, SearchIterator::UP search,
         size_t wanted_num_docs, size_t max_group_size,
         uint32_t current_id, uint32_t end_id)
{
    SearchIterator::UP limiter = limiter_factory.create_search(wanted_num_docs, max_group_size, PRE_FILTER);
    limiter = search->andWith(std::move(limiter), wanted_num_docs);
    if (limiter) {
        search = std::make_unique<LimitedSearchT<PRE_FILTER>>(std::move(limiter), std::move(search));
    }
    search->initRange(current_id + 1, end_id);
    return search;
}

} // namespace proton::matching::<unnamed>

SearchIterator::UP
MatchPhaseLimiter::maybe_limit(SearchIterator::UP search, double match_freq, size_t num_docs, Cursor * trace)
{
    size_t wanted_num_docs = _calculator.wanted_num_docs(match_freq);
    size_t max_filter_docs = static_cast<size_t>(num_docs * _maxFilterCoverage);
    size_t upper_limited_corpus_size = std::min(num_docs, max_filter_docs);
    if (trace) {
        trace->setDouble("hit_rate", match_freq); 
        trace->setLong("num_docs", num_docs);
        trace->setLong("max_filter_docs", max_filter_docs);
        trace->setLong("wanted_docs", wanted_num_docs);
    }
    if (upper_limited_corpus_size <= wanted_num_docs) {
        if (trace) {
            trace->setString("action", "Will not limit !");
        }
        LOG(debug, "Will not limit ! maybe_limit(hit_rate=%g, num_docs=%ld, max_filter_docs=%ld) = wanted_num_docs=%ld",
            match_freq, num_docs, max_filter_docs, wanted_num_docs);
        return search;
    }
    uint32_t current_id = search->getDocId();
    uint32_t end_id = search->getEndId();
    size_t total_query_hits = _calculator.estimated_hits(match_freq, num_docs);
    size_t max_group_size = _calculator.max_group_size(wanted_num_docs);
    bool use_pre_filter = (wanted_num_docs < (total_query_hits * _postFilterMultiplier));
    if (trace) {
        trace->setString("action", use_pre_filter ? "Will limit with prefix filter" : "Will limit with postfix filter");
        trace->setLong("max_group_size", max_group_size);
        trace->setLong("current_docid", current_id);
        trace->setLong("end_docid", end_id);
        trace->setLong("estimated_total_hits", total_query_hits);
    }
    LOG(debug, "Will do %s filter :  maybe_limit(hit_rate=%g, num_docs=%zu, max_filter_docs=%ld) = wanted_num_docs=%zu,"
        " max_group_size=%zu, current_docid=%u, end_docid=%u, total_query_hits=%ld",
        use_pre_filter ? "pre" : "post", match_freq, num_docs, max_filter_docs, wanted_num_docs,
        max_group_size, current_id, end_id, total_query_hits);
    return (use_pre_filter)
        ? do_limit<true>(_limiter_factory, std::move(search), wanted_num_docs, max_group_size, current_id, end_id)
        : do_limit<false>(_limiter_factory, std::move(search), wanted_num_docs, max_group_size, current_id, end_id);
}

void
MatchPhaseLimiter::updateDocIdSpaceEstimate(size_t searchedDocIdSpace, size_t remainingDocIdSpace)
{
    _coverage.update(searchedDocIdSpace, remainingDocIdSpace, _limiter_factory.getEstimatedHits());
}

size_t
MatchPhaseLimiter::getDocIdSpaceEstimate() const
{
    return _coverage.getEstimate();
}

}
