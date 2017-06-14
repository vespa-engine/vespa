// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/doom.h>

namespace proton {
namespace matching {

class QueryLimiter
{
private:
    typedef vespalib::Doom Doom;
public:
    class Token {
    public:
        typedef std::unique_ptr<Token> UP;
        virtual ~Token() { }
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
        virtual ~LimitedToken();
    };
    void grabToken(const Doom & doom);
    void releaseToken();
    vespalib::Monitor _monitor;
    volatile int _activeThreads;

    // These are updated asynchronously at reconfig.
    volatile int      _maxThreads;
    volatile double   _coverage;
    volatile uint32_t _minHits;
};

} // namespace matching
} // namespace proton

