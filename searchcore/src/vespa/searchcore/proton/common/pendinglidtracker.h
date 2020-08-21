// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <condition_variable>

namespace proton {

class PendingLidTracker {
public:
    class Token {
    public:
        Token(uint32_t lid, PendingLidTracker & tracker);
        Token(const Token &) = delete;
        Token & operator = (const Token &) = delete;
        Token(Token && rhs) noexcept
            : _tracker(rhs._tracker),
              _lid(rhs._lid)
        {
            rhs._tracker = nullptr;
        }
        ~Token();
    private:
        PendingLidTracker * _tracker;
        uint32_t            _lid;
    };
    PendingLidTracker();
    ~PendingLidTracker();
    Token produce(uint32_t lid);
    void waitForConsumedLid(uint32_t lid);
private:
    friend Token;
    void consume(uint32_t lid);
    std::mutex _mutex;
    std::condition_variable _cond;
    vespalib::hash_map<uint32_t, uint32_t> _pending;
};

}
