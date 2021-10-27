// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "catchresult.h"
#include "result.h"
#include <cassert>

namespace storage::spi {

CatchResult::CatchResult()
    : _promisedResult(),
      _resulthandler(nullptr)
{}
CatchResult::~CatchResult() = default;

void
CatchResult::onComplete(std::unique_ptr<Result> result) noexcept {
    _promisedResult.set_value(std::move(result));
}
void
CatchResult::addResultHandler(const ResultHandler * resultHandler) {
    assert(_resulthandler == nullptr);
    _resulthandler = resultHandler;
}

}
