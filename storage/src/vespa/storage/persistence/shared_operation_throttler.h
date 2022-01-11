// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>
#include <memory>
#include <optional>

namespace storage {

/**
 * Operation throttler that is intended to provide global throttling of
 * async operations across all persistence stripe threads. A throttler
 * wraps a logical max pending window size of in-flight operations. Depending
 * on the throttler implementation, the window size may expand and shrink
 * dynamically. Exactly how and when this happens is unspecified.
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

    // All methods are thread safe
    [[nodiscard]] virtual Token blocking_acquire_one() noexcept = 0;
    [[nodiscard]] virtual Token blocking_acquire_one(vespalib::duration timeout) noexcept = 0;
    [[nodiscard]] virtual Token try_acquire_one() noexcept = 0;

    // May return 0, in which case the window size is unlimited.
    [[nodiscard]] virtual uint32_t current_window_size() const noexcept = 0;

    // Exposed for unit testing only.
    [[nodiscard]] virtual uint32_t waiting_threads() const noexcept = 0;

    // Creates a throttler that does exactly zero throttling (but also has zero overhead and locking)
    static std::unique_ptr<SharedOperationThrottler> make_unlimited_throttler();
    // Creates a throttler that uses a MessageBus DynamicThrottlePolicy under the hood
    static std::unique_ptr<SharedOperationThrottler> make_dynamic_throttler(uint32_t min_size_and_window_increment);
private:
    // Exclusively called from a valid Token. Thread safe.
    virtual void release_one() noexcept = 0;
};

}
