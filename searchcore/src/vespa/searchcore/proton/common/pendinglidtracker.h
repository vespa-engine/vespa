// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <condition_variable>
#include <vector>

namespace proton {

class IPendingLidTracker {
public:
    class Token {
    public:
        Token();
        Token(uint32_t lid, IPendingLidTracker & tracker);
        Token(const Token &) = delete;
        Token & operator = (const Token &) = delete;
        Token & operator = (Token &&) = delete;
        Token(Token && rhs) noexcept
                : _tracker(rhs._tracker),
                  _lid(rhs._lid)
        {
            rhs._tracker = nullptr;
        }
        ~Token();
    private:
        IPendingLidTracker * _tracker;
        uint32_t            _lid;
    };
    virtual ~IPendingLidTracker() = default;
    virtual Token produce(uint32_t lid) = 0;
    virtual void waitForEmpty() = 0;
    virtual void waitForConsumed(uint32_t lid) = 0;
    virtual void waitForConsumed(const std::vector<uint32_t> & lids) = 0;
    virtual bool isInFlight(uint32_t lid) = 0;
    virtual bool areAnyInFlight(const std::vector<uint32_t> & lids) = 0;
    virtual bool areAnyInFlight() = 0;
private:
    virtual void consume(uint32_t lid) = 0;
    std::mutex                             _mutex;
    std::condition_variable                _cond;
    vespalib::hash_map<uint32_t, uint32_t> _pending;
};

class NoopLidTracker : public IPendingLidTracker {
public:
    Token produce(uint32_t lid) override;
    void waitForEmpty() override { }
    void waitForConsumed(uint32_t ) override { }
    void waitForConsumed(const std::vector<uint32_t> & ) override { }
    bool isInFlight(uint32_t ) override { return false; }
    bool areAnyInFlight(const std::vector<uint32_t> & ) override { return false; }
    bool areAnyInFlight() override { return false; }
private:
    void consume(uint32_t ) override { }
};

class PendingLidTracker : public IPendingLidTracker {
public:
    PendingLidTracker();
    ~PendingLidTracker() override;
    Token produce(uint32_t lid) override;
    void waitForEmpty() override;
    void waitForConsumed(uint32_t lid) override;
    void waitForConsumed(const std::vector<uint32_t> & lids) override;
    bool isInFlight(uint32_t lid) override;
    bool areAnyInFlight(const std::vector<uint32_t> & lids) override;
    bool areAnyInFlight() override;
private:
    using MonitorGuard = std::unique_lock<std::mutex>;
    void consume(uint32_t lid) override;
    void waitFor(MonitorGuard & guard, uint32_t lid);
    std::mutex                             _mutex;
    std::condition_variable                _cond;
    vespalib::hash_map<uint32_t, uint32_t> _pending;
};

}
