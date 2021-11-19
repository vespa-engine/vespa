// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_latch.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;
using storage::spi::Result;
using storage::spi::RemoveResult;

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

Result::UP
TransportMerger::merge(ResultUP accum, ResultUP incoming, bool documentWasFound) {
    return documentWasFound ? std::move(incoming) : std::move(accum);
}

void
TransportMerger::mergeWithLock(ResultUP result, bool documentWasFound) {
    if (!_result) {
        _result = std::move(result);
    } else if (result->hasError()) {
        _result = std::make_unique<Result>(mergeErrorResults(*_result, *result));
    } else {
        _result = merge(std::move(_result), std::move(result), documentWasFound);
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

AsyncTransportContext::AsyncTransportContext(uint32_t cnt, OperationComplete::UP onComplete)
    : TransportMerger(cnt > 1),
      _countDown(cnt),
      _onComplete(std::move(onComplete))
{
    if (cnt == 0u) {
        _onComplete->onComplete(std::make_unique<Result>());
    }
}

void
AsyncTransportContext::completeIfDone() {
    _countDown--;
    if (_countDown == 0) {
        _onComplete->onComplete(std::move(_result));
    }

}
AsyncTransportContext::~AsyncTransportContext() = default;

void
AsyncTransportContext::send(ResultUP result, bool documentWasFound)
{
    mergeResult(std::move(result), documentWasFound);
}

Result::UP
AsyncRemoveTransportContext::merge(ResultUP accum, ResultUP incoming, bool) {
    // TODO This can be static cast if necessary.
    dynamic_cast<RemoveResult *>(accum.get())->inc_num_removed(dynamic_cast<RemoveResult *>(incoming.get())->num_removed());
    return accum;
}

} // proton
