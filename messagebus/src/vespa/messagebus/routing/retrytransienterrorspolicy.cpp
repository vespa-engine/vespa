// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "retrytransienterrorspolicy.h"
#include <vespa/messagebus/errorcode.h>

namespace mbus {

RetryTransientErrorsPolicy::RetryTransientErrorsPolicy() :
    _enabled(true),
    _baseDelay(1.0)
{}

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
    return _baseDelay.load(std::memory_order_relaxed) * retry;
}

}

