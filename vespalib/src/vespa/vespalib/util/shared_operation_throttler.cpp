// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "shared_operation_throttler.h"
#include <atomic>
#include <condition_variable>
#include <cassert>
#include <functional>
#include <mutex>

namespace vespalib {

namespace {

class NoLimitsOperationThrottler final : public SharedOperationThrottler {
public:
    NoLimitsOperationThrottler()
        : SharedOperationThrottler(),
          _refs(0u)
    {
    }
    ~NoLimitsOperationThrottler() override {
        assert(_refs.load() == 0u);
    }
    Token blocking_acquire_one() noexcept override {
        internal_ref_count_increase();
        return Token(this, TokenCtorTag{});
    }
    Token blocking_acquire_one(vespalib::steady_time) noexcept override {
        internal_ref_count_increase();
        return Token(this, TokenCtorTag{});
    }
    Token try_acquire_one() noexcept override {
        internal_ref_count_increase();
        return Token(this, TokenCtorTag{});
    }
    uint32_t current_window_size() const noexcept override { return 0; }
    uint32_t current_active_token_count() const noexcept override {
        return _refs.load(std::memory_order_relaxed);
    }
    uint32_t waiting_threads() const noexcept override { return 0; }
    void reconfigure_dynamic_throttling(const DynamicThrottleParams&) noexcept override { /* no-op */ }
private:
    void internal_ref_count_increase() noexcept {
        // Relaxed semantics suffice, as there are no transitive memory visibility/ordering requirements.
        _refs.fetch_add(1u, std::memory_order_relaxed);
    }
    void release_one() noexcept override {
        _refs.fetch_sub(1u, std::memory_order_relaxed);
    }
    std::atomic<uint32_t> _refs;
};

/**
 * Effectively a 1-1 transplant of the MessageBus DynamicThrottlePolicy, but
 * without an underlying StaticThrottlePolicy and with no need for individual
 * MessageBus Message/Reply objects.
 *
 * Please keep the underlying algorithm in sync with the Java implementation,
 * as that is considered the source of truth. For descriptions of the various
 * parameters, also see the Java code:
 *   messagebus/src/main/java/com/yahoo/messagebus/DynamicThrottlePolicy.java
 */
class DynamicThrottlePolicy {
    SharedOperationThrottler::DynamicThrottleParams _active_params;
    std::function<steady_time()> _time_provider;
    uint32_t _num_sent;
    uint32_t _num_ok;
    double   _resize_rate;
    uint64_t _resize_time;
    uint64_t _time_of_last_message;
    uint64_t _idle_time_period;
    double   _efficiency_threshold;
    double   _window_size_increment;
    double   _window_size;
    double   _max_window_size;
    double   _min_window_size;
    double   _decrement_factor;
    double   _window_size_backoff;
    double   _weight;
    double   _local_max_throughput;
public:
    DynamicThrottlePolicy(const SharedOperationThrottler::DynamicThrottleParams& params,
                          std::function<steady_time()> time_provider);

    void set_window_size_increment(double window_size_increment) noexcept;
    void set_window_size_backoff(double window_size_backoff) noexcept;
    void set_resize_rate(double resize_rate) noexcept;
    void set_max_window_size(double max_size) noexcept;

    void set_min_window_size(double min_size) noexcept;
    void set_window_size_decrement_factor(double decrement_factor) noexcept;

    void configure(const SharedOperationThrottler::DynamicThrottleParams& params) noexcept;

    [[nodiscard]] uint32_t current_window_size() const noexcept {
        return static_cast<uint32_t>(_window_size);
    }
    [[nodiscard]] bool has_spare_capacity(uint32_t pending_count) noexcept;
    void process_request() noexcept;
    void process_response(bool success) noexcept;

private:
    void internal_unconditional_configure(const SharedOperationThrottler::DynamicThrottleParams& params) noexcept;

    [[nodiscard]] uint64_t current_time_as_millis() noexcept {
        return count_ms(_time_provider().time_since_epoch());
    }
};

DynamicThrottlePolicy::DynamicThrottlePolicy(const SharedOperationThrottler::DynamicThrottleParams& params,
                                             std::function<steady_time()> time_provider)
    : _active_params(params),
      _time_provider(std::move(time_provider)),
      _num_sent(0),
      _num_ok(0),
      _resize_rate(3.0),
      _resize_time(0),
      _time_of_last_message(current_time_as_millis()),
      _idle_time_period(60000),
      _efficiency_threshold(1),
      _window_size_increment(20),
      _window_size(_window_size_increment),
      _max_window_size(INT_MAX),
      _min_window_size(_window_size_increment),
      _decrement_factor(2.0),
      _window_size_backoff(0.9),
      _weight(1),
      _local_max_throughput(0)
{
    internal_unconditional_configure(_active_params);
}

void
DynamicThrottlePolicy::set_window_size_increment(double window_size_increment) noexcept
{
    _window_size_increment = window_size_increment;
    _window_size = std::max(_window_size, _window_size_increment);
}

void
DynamicThrottlePolicy::set_window_size_backoff(double window_size_backoff) noexcept
{
    _window_size_backoff = std::max(0.0, std::min(1.0, window_size_backoff));
}

void
DynamicThrottlePolicy::set_resize_rate(double resize_rate) noexcept
{
    _resize_rate = std::max(2.0, resize_rate);
}

void
DynamicThrottlePolicy::set_max_window_size(double max_size) noexcept
{
    _max_window_size = max_size;
}

void
DynamicThrottlePolicy::set_min_window_size(double min_size) noexcept
{
    _min_window_size = min_size;
    _window_size = std::max(_min_window_size, _window_size_increment);
}

void
DynamicThrottlePolicy::set_window_size_decrement_factor(double decrement_factor) noexcept
{
    _decrement_factor = decrement_factor;
}

void
DynamicThrottlePolicy::internal_unconditional_configure(const SharedOperationThrottler::DynamicThrottleParams& params) noexcept
{
    // We use setters for convenience, since setting one parameter may imply setting others,
    // based on it, and there's frequently min/max capping of values.
    set_window_size_increment(params.window_size_increment);
    set_min_window_size(params.min_window_size);
    set_max_window_size(params.max_window_size);
    set_resize_rate(params.resize_rate);
    set_window_size_decrement_factor(params.window_size_decrement_factor);
    set_window_size_backoff(params.window_size_backoff);
}

void
DynamicThrottlePolicy::configure(const SharedOperationThrottler::DynamicThrottleParams& params) noexcept
{
    // To avoid any noise where setting parameters on the throttler may implicitly reduce the
    // current window size (even though this isn't _currently_ the case), don't invoke any internal
    // reconfiguration code unless the parameters have actually changed.
    if (params != _active_params) {
        internal_unconditional_configure(params);
        _active_params = params;
    }
}

bool
DynamicThrottlePolicy::has_spare_capacity(uint32_t pending_count) noexcept
{
    const uint64_t time = current_time_as_millis();
    if ((time - _time_of_last_message) > _idle_time_period) {
        _window_size = std::max(_min_window_size, std::min(_window_size, pending_count + _window_size_increment));
    }
    _time_of_last_message = time;
    const auto window_size_floored = static_cast<uint32_t>(_window_size);
    // Use floating point window sizes, so the algorithm sees the difference between 1.1 and 1.9 window size.
    const bool carry = _num_sent < ((_window_size * _resize_rate) * (_window_size - window_size_floored));
    return pending_count < (window_size_floored + (carry ? 1 : 0));
}

void
DynamicThrottlePolicy::process_request() noexcept
{
    if (++_num_sent < (_window_size * _resize_rate)) {
        return;
    }

    const uint64_t time = current_time_as_millis();
    const double elapsed = time - _resize_time;
    _resize_time = time;

    const double throughput = _num_ok / elapsed;
    _num_sent = 0;
    _num_ok = 0;

    if (throughput > _local_max_throughput) {
        _local_max_throughput = throughput;
        _window_size += _weight * _window_size_increment;
    } else {
        // scale up/down throughput for comparing to window size
        double period = 1;
        while ((throughput * (period / _window_size)) < 2) {
            period *= 10;
        }
        while ((throughput * (period / _window_size)) > 2) {
            period *= 0.1;
        }
        const double efficiency = throughput * (period / _window_size);

        if (efficiency < _efficiency_threshold) {
            _window_size = std::min(_window_size * _window_size_backoff,
                                    _window_size - _decrement_factor * _window_size_increment);
            _local_max_throughput = 0;
        } else {
            _window_size += _weight * _window_size_increment;
        }
    }
    _window_size = std::max(_min_window_size, _window_size);
    _window_size = std::min(_max_window_size, _window_size);
}

void
DynamicThrottlePolicy::process_response(bool success) noexcept
{
    if (success) {
        ++_num_ok;
    }
}

class DynamicOperationThrottler final : public SharedOperationThrottler {
    mutable std::mutex      _mutex;
    std::condition_variable _cond;
    DynamicThrottlePolicy   _throttle_policy;
    uint32_t                _pending_ops;
    uint32_t                _waiting_threads;
public:
    DynamicOperationThrottler(const DynamicThrottleParams& params,
                              std::function<steady_time()> time_provider);
    ~DynamicOperationThrottler() override;

    Token blocking_acquire_one() noexcept override;
    Token blocking_acquire_one(vespalib::steady_time deadline) noexcept override;
    Token try_acquire_one() noexcept override;
    uint32_t current_window_size() const noexcept override;
    uint32_t current_active_token_count() const noexcept override;
    uint32_t waiting_threads() const noexcept override;
    void reconfigure_dynamic_throttling(const DynamicThrottleParams& params) noexcept override;
private:
    void release_one() noexcept override;
    // Non-const since actually checking the send window of a dynamic throttler might change
    // it if enough time has passed.
    [[nodiscard]] bool has_spare_capacity_in_active_window() noexcept;
    void add_one_to_active_window_size() noexcept;
    void subtract_one_from_active_window_size() noexcept;
};

DynamicOperationThrottler::DynamicOperationThrottler(const DynamicThrottleParams& params,
                                                     std::function<steady_time()> time_provider)
    : _mutex(),
      _cond(),
      _throttle_policy(params, std::move(time_provider)),
      _pending_ops(0),
      _waiting_threads(0)
{
}

DynamicOperationThrottler::~DynamicOperationThrottler()
{
    assert(_pending_ops == 0u);
}

bool
DynamicOperationThrottler::has_spare_capacity_in_active_window() noexcept
{
    return _throttle_policy.has_spare_capacity(_pending_ops);
}

void
DynamicOperationThrottler::add_one_to_active_window_size() noexcept
{
    _throttle_policy.process_request();
    ++_pending_ops;
}

void
DynamicOperationThrottler::subtract_one_from_active_window_size() noexcept
{
    _throttle_policy.process_response(true); // TODO support failure push-back
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
DynamicOperationThrottler::blocking_acquire_one(vespalib::steady_time deadline) noexcept
{
    std::unique_lock lock(_mutex);
    if (!has_spare_capacity_in_active_window()) {
        ++_waiting_threads;
        const bool accepted = _cond.wait_until(lock, deadline, [&] {
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
    return _throttle_policy.current_window_size();
}

uint32_t
DynamicOperationThrottler::current_active_token_count() const noexcept
{
    std::unique_lock lock(_mutex);
    return _pending_ops;
}

uint32_t
DynamicOperationThrottler::waiting_threads() const noexcept
{
    std::unique_lock lock(_mutex);
    return _waiting_threads;
}

void
DynamicOperationThrottler::reconfigure_dynamic_throttling(const DynamicThrottleParams& params) noexcept
{
    std::unique_lock lock(_mutex);
    _throttle_policy.configure(params);
}

} // anonymous namespace

std::unique_ptr<SharedOperationThrottler>
SharedOperationThrottler::make_unlimited_throttler()
{
    return std::make_unique<NoLimitsOperationThrottler>();
}

std::unique_ptr<SharedOperationThrottler>
SharedOperationThrottler::make_dynamic_throttler(const DynamicThrottleParams& params)
{
    return std::make_unique<DynamicOperationThrottler>(params, []() noexcept { return steady_clock::now(); });
}

std::unique_ptr<SharedOperationThrottler>
SharedOperationThrottler::make_dynamic_throttler(const DynamicThrottleParams& params,
                                                 std::function<steady_time()> time_provider)
{
    return std::make_unique<DynamicOperationThrottler>(params, std::move(time_provider));
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
