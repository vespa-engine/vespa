// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "catchresult.h"

#include "result.h"

#include <vespa/check_require.h>

namespace storage::spi {

CatchResult::CatchResult() : _promisedResult(), _resulthandler(nullptr) {
}
CatchResult::~CatchResult() = default;

void CatchResult::onComplete(std::unique_ptr<Result> result) noexcept {
    _promisedResult.set_value(std::move(result));
}
void CatchResult::addResultHandler(const ResultHandler* resultHandler) {
    CHECK(_resulthandler == nullptr);
    _resulthandler = resultHandler;
}

} // namespace storage::spi
