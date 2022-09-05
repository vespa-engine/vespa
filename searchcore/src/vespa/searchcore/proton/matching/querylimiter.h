// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/util/doom.h>
#include <mutex>
#include <condition_variable>

namespace proton::matching {

class QueryLimiter
{
private:
    typedef vespalib::Doom Doom;
public:
    class Token {
    public:
        typedef std::unique_ptr<Token> UP;
        virtual ~Token() = default;
    };
public:
    QueryLimiter();
    void configure(int maxThreads, double coverage, uint32_t minHits);
    Token::UP getToken(const Doom & doom, uint32_t numDocs, uint32_t numHits, bool hasSorting, bool hasGrouping);
private:
    class NoLimitToken : public Token {
    };
    class LimitedToken : public Token {
    private:
        QueryLimiter & _limiter;
    public:
        LimitedToken(const Doom & doom, QueryLimiter & limiter);
        LimitedToken(const NoLimitToken &) = delete;
        LimitedToken & operator =(const NoLimitToken &) = delete;
        ~LimitedToken() override;
    };
    void grabToken(const Doom & doom);
    void releaseToken();
    std::mutex              _lock;
    std::condition_variable _cond;
    int _activeThreads;

    // These are updated asynchronously at reconfig.
    std::atomic<int>      _maxThreads;
    std::atomic<double>   _coverage;
    std::atomic<uint32_t> _minHits;

    [[nodiscard]] int get_max_threads() const noexcept { return _maxThreads.load(std::memory_order_relaxed); }
    [[nodiscard]] double get_coverage() const noexcept { return _coverage.load(std::memory_order_relaxed); }
    [[nodiscard]] uint32_t get_min_hits() const noexcept { return _minHits.load(std::memory_order_relaxed); }
};

}
