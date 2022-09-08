// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "match_phase_limit_calculator.h"
#include "attribute_limiter.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <atomic>

namespace proton::matching {

class RangeQueryLocator;

class LimitedSearch : public search::queryeval::SearchIterator {
public:
    LimitedSearch(SearchIterator::UP first, SearchIterator::UP second) :
        _first(std::move(first)),
        _second(std::move(second))
    {
    }
    void doSeek(uint32_t docId) override;
    void initRange(uint32_t begin, uint32_t end) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const SearchIterator &  getFirst() const { return *_first; }
    const SearchIterator & getSecond() const { return *_second; }
    SearchIterator &  getFirst() { return *_first; }
    SearchIterator & getSecond() { return *_second; }
private:
    SearchIterator::UP _first;
    SearchIterator::UP _second;
};

/**
 * Interface defining how we intend to use the match phase limiter
 * functionality. The first step is to check whether we should enable
 * this functionality at all. If enabled; we need to match some hits
 * in each match thread for estimation purposes. The total number of
 * matches (hits) and the total document space searched (docs) are
 * aggregated across all match threads and each match thread will use
 * the maybe_limit function to possibly augment its iterator tree to
 * limit the number of matches.
 **/
struct MaybeMatchPhaseLimiter {
    using Cursor = vespalib::slime::Cursor;
    typedef search::queryeval::SearchIterator SearchIterator;
    typedef std::unique_ptr<MaybeMatchPhaseLimiter> UP;
    virtual bool is_enabled() const = 0;
    virtual bool was_limited() const = 0;
    virtual size_t sample_hits_per_thread(size_t num_threads) const = 0;
    virtual SearchIterator::UP maybe_limit(SearchIterator::UP search, double match_freq, size_t num_docs, Cursor * trace) = 0;
    virtual void updateDocIdSpaceEstimate(size_t searchedDocIdSpace, size_t remainingDocIdSpace) = 0;
    virtual size_t getDocIdSpaceEstimate() const = 0;
    virtual ~MaybeMatchPhaseLimiter() = default;
};

/**
 * This class is used when match phase limiting is not configured.
 **/
struct NoMatchPhaseLimiter : MaybeMatchPhaseLimiter {
    bool is_enabled() const override { return false; }
    bool was_limited() const override { return false; }
    size_t sample_hits_per_thread(size_t) const override { return 0; }
    SearchIterator::UP maybe_limit(SearchIterator::UP search, double, size_t, Cursor *) override {
        return search;
    }
    void updateDocIdSpaceEstimate(size_t, size_t) override { }
    size_t getDocIdSpaceEstimate() const override { return std::numeric_limits<size_t>::max(); }
};

struct DiversityParams {
    using CutoffStrategy = AttributeLimiter::DiversityCutoffStrategy;
    DiversityParams() : DiversityParams("", 0, 0, CutoffStrategy::LOOSE) { }
    DiversityParams(const vespalib::string & attribute_, uint32_t min_groups_,
                    double cutoff_factor_, CutoffStrategy cutoff_strategy_)
        : attribute(attribute_),
          min_groups(min_groups_),
          cutoff_factor(cutoff_factor_),
          cutoff_strategy(cutoff_strategy_)
    { }
    bool enabled() const { return !attribute.empty() && (min_groups > 0); }
    vespalib::string  attribute;
    uint32_t          min_groups;
    double            cutoff_factor;
    CutoffStrategy    cutoff_strategy;
};

struct DegradationParams {
    DegradationParams(const vespalib::string &attribute_, size_t max_hits_, bool descending_,
                      double max_filter_coverage_, double sample_percentage_, double post_filter_multiplier_)
        : attribute(attribute_),
          descending(descending_),
          max_hits(max_hits_),
          max_filter_coverage(max_filter_coverage_),
          sample_percentage(sample_percentage_),
          post_filter_multiplier(post_filter_multiplier_)
    { }
    bool enabled() const { return !attribute.empty() && (max_hits > 0); }
    vespalib::string attribute;
    bool             descending;
    size_t           max_hits;
    double           max_filter_coverage;
    double           sample_percentage;
    double           post_filter_multiplier;
};

/**
 * This class is is used when rank phase limiting is configured.
 **/
class MatchPhaseLimiter : public MaybeMatchPhaseLimiter
{
private:
    class Coverage {
    public:
        explicit Coverage(uint32_t docIdLimit) :
            _docIdLimit(docIdLimit),
            _searched(0)
        { }
        void update(size_t searched, size_t remaining, ssize_t hits) {
            if (hits >= 0) {
                _searched += (searched + (hits*remaining)/_docIdLimit);
            } else {
                _searched += (searched + remaining);
            }
        }
        uint32_t getEstimate() const { return _searched; }
    private:
        const uint32_t        _docIdLimit;
        std::atomic<uint32_t> _searched;
    };
    const double              _postFilterMultiplier;
    const double              _maxFilterCoverage;
    MatchPhaseLimitCalculator _calculator;
    AttributeLimiter          _limiter_factory;
    Coverage                  _coverage;


public:
    MatchPhaseLimiter(uint32_t docIdLimit,
                      const RangeQueryLocator & rangeQueryLocator,
                      search::queryeval::Searchable &searchable_attributes,
                      search::queryeval::IRequestContext & requestContext,
                      const DegradationParams & degradation,
                      const DiversityParams & diversity);
    ~MatchPhaseLimiter() override;
    bool is_enabled() const override { return true; }
    bool was_limited() const override { return _limiter_factory.was_used(); }
    size_t sample_hits_per_thread(size_t num_threads) const override {
        return _calculator.sample_hits_per_thread(num_threads);
    }
    SearchIterator::UP maybe_limit(SearchIterator::UP search, double match_freq, size_t num_docs, Cursor * trace) override;
    void updateDocIdSpaceEstimate(size_t searchedDocIdSpace, size_t remainingDocIdSpace) override;
    size_t getDocIdSpaceEstimate() const override;
};

}
