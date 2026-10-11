// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/doom.h>

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <memory>
#include <mutex>

namespace proton::matching {

/**
 * Limits the number of match threads doing memory-heavy work at the same time.
 *
 * Capacity is counted in units (one unit per thread). Waiters are served in
 * FIFO order, so a request for many units is not starved by a stream of
 * single-unit requests. The thread that frees capacity, by releasing a token
 * or by reconfiguration, hands it to the waiters at the front of the queue,
 * in order, while their requests fit, and wakes only those. A waiter whose
 * query reaches hard doom leaves the queue and is let through regardless of
 * capacity.
 *
 * A holder of a token must never wait for another thread that may itself be
 * waiting for a token; doing so can deadlock until hard doom.
 **/
class QueryLimiter {
private:
    using Doom = vespalib::Doom;

public:
    class Token {
    public:
        using UP = std::unique_ptr<Token>;
        virtual ~Token() = default;
    };

public:
    QueryLimiter();
    void configure(int maxThreads, double coverage, uint32_t minHits);
    Token::UP getToken(const Doom& doom, uint32_t numDocs, uint32_t numHits, bool hasSorting, bool hasGrouping);

    /**
     * Unconditionally (when limiting is enabled) take a token covering a whole
     * query running numThreads threads. The token holds numThreads units,
     * capped at the configured max so that it can always be granted.
     *
     * Unlike getToken, the coverage and min hits heuristics are not applied,
     * since the number of hits is not known before matching. This is a
     * deliberate trade-off: small queries pay for admission too.
     **/
    Token::UP getQueryToken(const Doom& doom, uint32_t numThreads);

    // for testing
    [[nodiscard]] int active_threads();
    [[nodiscard]] size_t num_waiting();

private:
    class NoLimitToken : public Token {};
    class LimitedToken : public Token {
    private:
        QueryLimiter& _limiter;
        int           _units;

    public:
        LimitedToken(const Doom& doom, QueryLimiter& limiter, int units);
        LimitedToken(const NoLimitToken&) = delete;
        LimitedToken& operator=(const NoLimitToken&) = delete;
        ~LimitedToken() override;
    };
    /*
     * A thread waiting for capacity. The record lives on the waiting thread's
     * stack, and every access is made with _lock held, so its owner cannot
     * return and destroy it while a granting thread still uses it. That is
     * why grants notify with the lock held.
     */
    struct Waiter {
        int                     units; // as requested; capped only when granted
        int                     granted;
        bool                    done;
        std::condition_variable cond;
        explicit Waiter(int units_in) : units(units_in), granted(0), done(false), cond() {}
    };
    int grabToken(const Doom& doom, int units);
    void releaseToken(int units);
    [[nodiscard]] int grant_for(int units) const noexcept;
    [[nodiscard]] bool fits(int granted) const noexcept; // _lock must be held
    void grant_front(); // _lock must be held
    std::mutex          _lock;
    int                 _activeThreads;
    std::deque<Waiter*> _waiting; // FIFO

    // These are updated asynchronously at reconfig.
    std::atomic<int>      _maxThreads;
    std::atomic<double>   _coverage;
    std::atomic<uint32_t> _minHits;

    [[nodiscard]] int get_max_threads() const noexcept { return _maxThreads.load(std::memory_order_relaxed); }
    [[nodiscard]] double get_coverage() const noexcept { return _coverage.load(std::memory_order_relaxed); }
    [[nodiscard]] uint32_t get_min_hits() const noexcept { return _minHits.load(std::memory_order_relaxed); }
};

} // namespace proton::matching
