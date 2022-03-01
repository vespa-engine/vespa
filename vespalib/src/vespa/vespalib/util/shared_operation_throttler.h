// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * All operations on the throttler are thread safe.
 */
class SharedOperationThrottler {
protected:
    struct TokenCtorTag {}; // Make available to subclasses for token construction.
public:
    class Token {
        SharedOperationThrottler* _throttler;
    public:
        constexpr Token(SharedOperationThrottler* throttler, TokenCtorTag) noexcept : _throttler(throttler) {}
        constexpr Token() noexcept : _throttler(nullptr) {}
        constexpr Token(Token&& rhs) noexcept
            : _throttler(rhs._throttler)
        {
            rhs._throttler = nullptr;
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
    [[nodiscard]] virtual Token blocking_acquire_one() noexcept = 0;
    // Attempt to acquire a valid throttling token, waiting up to `timeout` for one to be
    // available. If the deadline is reached without any tokens becoming available, an
    // invalid token will be returned.
    [[nodiscard]] virtual Token blocking_acquire_one(vespalib::steady_time deadline) noexcept = 0;
    // Attempt to acquire a valid throttling token if one is immediately available.
    // An invalid token will be returned if none is available. Never blocks (other than
    // when contending for the internal throttler mutex).
    [[nodiscard]] virtual Token try_acquire_one() noexcept = 0;

    // May return 0, in which case the window size is unlimited.
    [[nodiscard]] virtual uint32_t current_window_size() const noexcept = 0;

    [[nodiscard]] virtual uint32_t current_active_token_count() const noexcept = 0;

    [[nodiscard]] virtual uint32_t waiting_threads() const noexcept = 0;

    struct DynamicThrottleParams {
        uint32_t window_size_increment      = 20;
        uint32_t min_window_size            = 20;
        uint32_t max_window_size            = INT_MAX; // signed max to be 1-1 compatible with Java defaults
        double resize_rate                  = 3.0;
        double window_size_decrement_factor = 1.2;
        double window_size_backoff          = 0.95;

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
    virtual void release_one() noexcept = 0;
};

}
