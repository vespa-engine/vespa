// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "updatehook.h"

namespace metrics {

MetricLockGuard::MetricLockGuard(std::mutex & mutex)
    : _guard(mutex)
{}

bool
MetricLockGuard::owns(const std::mutex & mutex) const {
    return (_guard.mutex() == &mutex) && _guard.owns_lock();
}

MetricLockGuard::~MetricLockGuard() = default;

}
