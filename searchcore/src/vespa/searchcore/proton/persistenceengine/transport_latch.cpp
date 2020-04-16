// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_latch.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;
using storage::spi::Result;

namespace proton {

TransportLatch::TransportLatch(uint32_t cnt)
    : _latch(cnt),
      _lock(),
      _result()
{
    if (cnt == 0u) {
        _result = std::make_unique<Result>();
    }
}

TransportLatch::~TransportLatch() = default;

void
TransportLatch::send(ResultUP result, bool documentWasFound)
{
    {
        std::lock_guard<std::mutex> guard(_lock);
        if (!_result) {
            _result = std::move(result);
        } else if (result->hasError()) {
            _result.reset(new Result(mergeErrorResults(*_result, *result)));
        } else if (documentWasFound) {
            _result = std::move(result);
        }
    }
    _latch.countDown();
}

Result
TransportLatch::mergeErrorResults(const Result &lhs, const Result &rhs)
{
    Result::ErrorType error = (lhs.getErrorCode() > rhs.getErrorCode() ? lhs : rhs).getErrorCode();
    return Result(error, make_string("%s, %s", lhs.getErrorMessage().c_str(), rhs.getErrorMessage().c_str()));
}

} // proton
