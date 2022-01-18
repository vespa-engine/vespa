// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "shared_operation_throttler.h"
#include <vespa/messagebus/dynamicthrottlepolicy.h>
#include <vespa/storage/common/dummy_mbus_messages.h>
#include <condition_variable>
#include <cassert>
#include <mutex>

namespace storage {

namespace {

class NoLimitsOperationThrottler final : public SharedOperationThrottler {
public:
    ~NoLimitsOperationThrottler() override = default;
    Token blocking_acquire_one() noexcept override {
        return Token(this, TokenCtorTag{});
    }
    Token blocking_acquire_one(vespalib::duration) noexcept override {
        return Token(this, TokenCtorTag{});
    }
    Token try_acquire_one() noexcept override {
        return Token(this, TokenCtorTag{});
    }
    uint32_t current_window_size() const noexcept override { return 0; }
    uint32_t waiting_threads() const noexcept override { return 0; }
private:
    void release_one() noexcept override { /* no-op */ }
};

class DynamicOperationThrottler final : public SharedOperationThrottler {
    mutable std::mutex          _mutex;
    std::condition_variable     _cond;
    mbus::DynamicThrottlePolicy _throttle_policy;
    uint32_t                    _pending_ops;
    uint32_t                    _waiting_threads;
public:
    explicit DynamicOperationThrottler(uint32_t min_size_and_window_increment);
    ~DynamicOperationThrottler() override;

    Token blocking_acquire_one() noexcept override;
    Token blocking_acquire_one(vespalib::duration timeout) noexcept override;
    Token try_acquire_one() noexcept override;
    uint32_t current_window_size() const noexcept override;
    uint32_t waiting_threads() const noexcept override;
private:
    void release_one() noexcept override;
    // Non-const since actually checking the send window of a dynamic throttler might change
    // it if enough time has passed.
    [[nodiscard]] bool has_spare_capacity_in_active_window() noexcept;
    void add_one_to_active_window_size();
    void subtract_one_from_active_window_size();
};

DynamicOperationThrottler::DynamicOperationThrottler(uint32_t min_size_and_window_increment)
    : _mutex(),
      _cond(),
      _throttle_policy(static_cast<double>(min_size_and_window_increment)),
      _pending_ops(0),
      _waiting_threads(0)
{
    _throttle_policy.setWindowSizeDecrementFactor(1.2);
    _throttle_policy.setWindowSizeBackOff(0.95);
}

DynamicOperationThrottler::~DynamicOperationThrottler() = default;

bool
DynamicOperationThrottler::has_spare_capacity_in_active_window() noexcept
{
    DummyMbusRequest dummy_request;
    return _throttle_policy.canSend(dummy_request, _pending_ops);
}

void
DynamicOperationThrottler::add_one_to_active_window_size()
{
    DummyMbusRequest dummy_request;
    _throttle_policy.processMessage(dummy_request);
    ++_pending_ops;
}

void
DynamicOperationThrottler::subtract_one_from_active_window_size()
{
    DummyMbusReply dummy_reply;
    _throttle_policy.processReply(dummy_reply);
    assert(_pending_ops > 0);
    --_pending_ops;
}

DynamicOperationThrottler::Token
DynamicOperationThrottler::blocking_acquire_one() noexcept
{
    std::unique_lock lock(_mutex);
    if (!has_spare_capacity_in_active_window()) {
        ++_waiting_threads;
        _cond.wait(lock, [&] {
            return has_spare_capacity_in_active_window();
        });
        --_waiting_threads;
    }
    add_one_to_active_window_size();
    return Token(this, TokenCtorTag{});
}

DynamicOperationThrottler::Token
DynamicOperationThrottler::blocking_acquire_one(vespalib::duration timeout) noexcept
{
    std::unique_lock lock(_mutex);
    if (!has_spare_capacity_in_active_window()) {
        ++_waiting_threads;
        const bool accepted = _cond.wait_for(lock, timeout, [&] {
            return has_spare_capacity_in_active_window();
        });
        --_waiting_threads;
        if (!accepted) {
            return Token();
        }
    }
    add_one_to_active_window_size();
    return Token(this, TokenCtorTag{});
}

DynamicOperationThrottler::Token
DynamicOperationThrottler::try_acquire_one() noexcept
{
    std::unique_lock lock(_mutex);
    if (!has_spare_capacity_in_active_window()) {
        return Token();
    }
    add_one_to_active_window_size();
    return Token(this, TokenCtorTag{});
}

void
DynamicOperationThrottler::release_one() noexcept
{
    std::unique_lock lock(_mutex);
    subtract_one_from_active_window_size();
    // Only wake up a waiting thread if doing so would possibly result in success.
    if ((_waiting_threads > 0) && has_spare_capacity_in_active_window()) {
        lock.unlock();
        _cond.notify_one();
    }
}

uint32_t
DynamicOperationThrottler::current_window_size() const noexcept
{
    std::unique_lock lock(_mutex);
    return _throttle_policy.getMaxPendingCount(); // Actually returns current window size
}

uint32_t
DynamicOperationThrottler::waiting_threads() const noexcept
{
    std::unique_lock lock(_mutex);
    return _waiting_threads;
}

}

std::unique_ptr<SharedOperationThrottler>
SharedOperationThrottler::make_unlimited_throttler()
{
    return std::make_unique<NoLimitsOperationThrottler>();
}

std::unique_ptr<SharedOperationThrottler>
SharedOperationThrottler::make_dynamic_throttler(uint32_t min_size_and_window_increment)
{
    return std::make_unique<DynamicOperationThrottler>(min_size_and_window_increment);
}

DynamicOperationThrottler::Token::~Token()
{
    if (_throttler) {
        _throttler->release_one();
    }
}

void
DynamicOperationThrottler::Token::reset() noexcept
{
    if (_throttler) {
        _throttler->release_one();
        _throttler = nullptr;
    }
}

DynamicOperationThrottler::Token&
DynamicOperationThrottler::Token::operator=(Token&& rhs) noexcept
{
    reset();
    _throttler = rhs._throttler;
    rhs._throttler = nullptr;
    return *this;
}

}
