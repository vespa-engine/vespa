// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iretrypolicy.h"
#include <atomic>

namespace mbus {

/**
 * Implements a retry policy that allows resending of any error that is not fatal. It also does progressive
 * back-off, delaying each attempt by the given time multiplied by the retry attempt.
 */
class RetryTransientErrorsPolicy : public IRetryPolicy {
private:
    std::atomic<bool>   _enabled;
    std::atomic<double> _baseDelay;

public:

    using SP = std::shared_ptr<RetryTransientErrorsPolicy>;
    /**
     * Constructs a new instance of this policy. By default retries are enabled with a 1.0 second base delay.
     */
    RetryTransientErrorsPolicy();
    ~RetryTransientErrorsPolicy() override;

    /**
     * Sets whether or not this policy should allow retries or not.
     *
     * @param enabled True to allow retries.
     * @return This, to allow chaining.
     */
    RetryTransientErrorsPolicy &setEnabled(bool enabled);

    /**
     * Sets the base delay in seconds to wait between retries. This amount is multiplied by the retry number.
     *
     * @param baseDelay The time in seconds.
     * @return This, to allow chaining.
     */
    RetryTransientErrorsPolicy &setBaseDelay(double baseDelay);

    bool canRetry(uint32_t errorCode) const override;
    double getRetryDelay(uint32_t retry) const override;
};

} // namespace mbus

