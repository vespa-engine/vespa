// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "retrytransienterrorspolicy.h"
#include <vespa/messagebus/errorcode.h>

namespace mbus {

RetryTransientErrorsPolicy::RetryTransientErrorsPolicy() :
    _enabled(true),
    _baseDelay(0.001)
{}

RetryTransientErrorsPolicy::~RetryTransientErrorsPolicy() = default;

RetryTransientErrorsPolicy &
RetryTransientErrorsPolicy::setEnabled(bool enabled) {
    _enabled = enabled;
    return *this;
}

RetryTransientErrorsPolicy &
RetryTransientErrorsPolicy::setBaseDelay(double baseDelay) {
    _baseDelay = baseDelay;
    return *this;
}

bool
RetryTransientErrorsPolicy::canRetry(uint32_t errorCode) const {
    return _enabled.load(std::memory_order_relaxed) && errorCode < ErrorCode::FATAL_ERROR;
}

double
RetryTransientErrorsPolicy::getRetryDelay(uint32_t retry) const {
    uint64_t retryMultiplier = 0l;
    if (retry > 1) {
        retryMultiplier = 1L << std::min(20u, retry-1);
    }
    return std::min(10.0, _baseDelay.load(std::memory_order_relaxed) * retryMultiplier);
}

}

