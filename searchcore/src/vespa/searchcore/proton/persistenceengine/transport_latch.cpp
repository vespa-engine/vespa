// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_latch.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;
using storage::spi::Result;

namespace proton {

std::unique_ptr<std::mutex> createOptionalLock(bool needLocking) {
    return needLocking
        ? std::make_unique<std::mutex>()
        : std::unique_ptr<std::mutex>();
}
TransportMerger::TransportMerger(bool needLocking)
    : _result(),
      _lock(createOptionalLock(needLocking))
{
}
TransportMerger::~TransportMerger() = default;

void
TransportMerger::mergeResult(ResultUP result, bool documentWasFound) {
    if (_lock) {
        std::lock_guard<std::mutex> guard(*_lock);
        mergeWithLock(std::move(result), documentWasFound);
    } else {
        mergeWithLock(std::move(result), documentWasFound);
    }
}

void
TransportMerger::mergeWithLock(ResultUP result, bool documentWasFound) {
    if (!_result) {
        _result = std::move(result);
    } else if (result->hasError()) {
        _result = std::make_unique<Result>(mergeErrorResults(*_result, *result));
    } else if (documentWasFound) {
        _result = std::move(result);
    }
    completeIfDone();
}

Result
TransportMerger::mergeErrorResults(const Result &lhs, const Result &rhs)
{
    Result::ErrorType error = (lhs.getErrorCode() > rhs.getErrorCode() ? lhs : rhs).getErrorCode();
    return Result(error, make_string("%s, %s", lhs.getErrorMessage().c_str(), rhs.getErrorMessage().c_str()));
}

TransportLatch::TransportLatch(uint32_t cnt)
    : TransportMerger(cnt > 1),
      _latch(cnt)
{
    if (cnt == 0u) {
        _result = std::make_unique<Result>();
    }
}

TransportLatch::~TransportLatch() = default;

void
TransportLatch::send(ResultUP result, bool documentWasFound)
{
    mergeResult(std::move(result), documentWasFound);
    _latch.countDown();
}

AsyncTranportContext::AsyncTranportContext(uint32_t cnt, OperationComplete::UP onComplete)
    : TransportMerger(cnt > 1),
      _countDown(cnt),
      _onComplete(std::move(onComplete))
{
    if (cnt == 0u) {
        _onComplete->onComplete(std::make_unique<Result>());
    }
}

void
AsyncTranportContext::completeIfDone() {
    _countDown--;
    if (_countDown == 0) {
        _onComplete->onComplete(std::move(_result));
    }

}
AsyncTranportContext::~AsyncTranportContext() = default;

void
AsyncTranportContext::send(ResultUP result, bool documentWasFound)
{
    mergeResult(std::move(result), documentWasFound);
}

} // proton
