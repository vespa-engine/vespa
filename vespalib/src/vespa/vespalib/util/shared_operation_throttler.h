// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "time.h"
#include <functional>
#include <memory>
#include <optional>
#include <limits.h>

namespace vespalib {

/**
 * Operation throttler that is intended to provide global throttling of
 * async operations across multiple threads. A throttler wraps a logical
 * max pending window size of in-flight operations. Depending on the
 * throttler implementation, the window size may expand and shrink dynamically.
 * Exactly how and when this happens is unspecified.
 *
 * Offers both polling and (timed, non-timed) blocking calls for acquiring
 * a throttle token. If the returned token is valid, the caller may proceed
 * to invoke the asynchronous operation.
 *
 * The window slot taken up by a valid throttle token is implicitly freed up
 * when the token is destroyed.
 *
 * Additionally, the throttler may be configured with an absolute resource usage
 * limit, for some unspecified resource unit. Each token may consume a particular
 * amount of resources. If acquiring a token would cause the total resource
 * usage of the throttler to exceed the limit, token acquisition will either
 * fail (when using try_acquire) or block. Note that this is a _soft_ limit;
 * at least one token will always be allowed to be acquired, even if that single
 * token may by itself exceed the configured resource limit. This is to ensure
 * liveness of the system using the throttler.
 *
 * By default, there is no resource limit configured. Regardless of configured
 * limits, if tokens specify a non-zero resource usage, the total usage across
 * all tokens is still tracked and can be used for metrics etc.
 *
 * All operations on the throttler are thread safe.
 */
class SharedOperationThrottler {
protected:
    struct TokenCtorTag {}; // Make available to subclasses for token construction.
public:
    class Token {
        SharedOperationThrottler* _throttler;
        uint64_t                  _operation_resource_usage;
    public:
        constexpr Token(SharedOperationThrottler* throttler, uint64_t operation_resource_usage, TokenCtorTag) noexcept
            : _throttler(throttler),
              _operation_resource_usage(operation_resource_usage)
        {}
        constexpr Token() noexcept : _throttler(nullptr), _operation_resource_usage(0) {}
        constexpr Token(Token&& rhs) noexcept
            : _throttler(rhs._throttler),
              _operation_resource_usage(rhs._operation_resource_usage)
        {
            rhs._throttler = nullptr;
            rhs._operation_resource_usage = 0;
        }
        Token& operator=(Token&& rhs) noexcept;
        ~Token();

        Token(const Token&) = delete;
        Token& operator=(const Token&) = delete;

        [[nodiscard]] constexpr bool valid() const noexcept { return (_throttler != nullptr); }
        void reset() noexcept;
    };

    virtual ~SharedOperationThrottler() = default;

    // Acquire a valid throttling token, uninterruptedly blocking until one can be obtained.
    [[nodiscard]] virtual Token blocking_acquire_one(uint64_t operation_resource_usage) noexcept = 0;
    [[nodiscard]] Token blocking_acquire_one() noexcept {
        return blocking_acquire_one(0);
    }
    // Attempt to acquire a valid throttling token, waiting up to `timeout` for one to be
    // available. If the deadline is reached without any tokens becoming available, an
    // invalid token will be returned.
    [[nodiscard]] virtual Token blocking_acquire_one(vespalib::steady_time deadline,
                                                     uint64_t operation_resource_usage) noexcept = 0;
    [[nodiscard]] Token blocking_acquire_one(vespalib::steady_time deadline) noexcept {
        return blocking_acquire_one(deadline, 0);
    }
    // Attempt to acquire a valid throttling token if one is immediately available.
    // An invalid token will be returned if none is available. Never blocks (other than
    // when contending for the internal throttler mutex).
    [[nodiscard]] virtual Token try_acquire_one(uint64_t operation_resource_usage) noexcept = 0;
    [[nodiscard]] Token try_acquire_one() noexcept {
        return try_acquire_one(0);
    }

    // May return 0, in which case the window size is unlimited.
    [[nodiscard]] virtual uint32_t current_window_size() const noexcept = 0;

    [[nodiscard]] virtual uint32_t current_active_token_count() const noexcept = 0;

    [[nodiscard]] virtual uint32_t waiting_threads() const noexcept = 0;

    [[nodiscard]] virtual uint64_t current_resource_usage() const noexcept = 0;

    [[nodiscard]] virtual uint64_t max_resource_usage() const noexcept = 0;

    struct DynamicThrottleParams {
        uint32_t window_size_increment      = 20;
        uint32_t min_window_size            = 20;
        uint32_t max_window_size            = INT_MAX; // signed max to be 1-1 compatible with Java defaults
        double resize_rate                  = 3.0;
        double window_size_decrement_factor = 1.2;
        double window_size_backoff          = 0.95;

        // Soft limit of some undefined unit of resource usage. The limit is soft in
        // that at least 1 operation must always be possible to schedule, even if this
        // single operation by itself exceeds the usage limit. A limit of 0 means no
        // limit is enforced.
        uint64_t resource_usage_soft_limit  = 0;

        bool operator==(const DynamicThrottleParams&) const noexcept = default;
        bool operator!=(const DynamicThrottleParams&) const noexcept = default;
    };

    // No-op if underlying throttler does not use a dynamic policy, or if the supplied
    // parameters are equal to the current configuration.
    // FIXME leaky abstraction alert!
    virtual void reconfigure_dynamic_throttling(const DynamicThrottleParams& params) noexcept = 0;

    // Creates a throttler that does exactly zero throttling (but also has zero overhead and locking)
    static std::unique_ptr<SharedOperationThrottler> make_unlimited_throttler();

    // Creates a throttler that uses a DynamicThrottlePolicy under the hood
    static std::unique_ptr<SharedOperationThrottler> make_dynamic_throttler(const DynamicThrottleParams& params);
    static std::unique_ptr<SharedOperationThrottler> make_dynamic_throttler(const DynamicThrottleParams& params,
                                                                            std::function<steady_time()> time_provider);
private:
    // Exclusively called from a valid Token. Thread safe.
    virtual void release_one(uint64_t operation_resource_usage) noexcept = 0;
};

}
