// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "apply_bucket_diff_entry_complete.h"
#include <vespa/persistence/spi/result.h>
#include <cassert>

namespace storage {

ApplyBucketDiffEntryComplete::ApplyBucketDiffEntryComplete(ResultPromise result_promise, const framework::Clock& clock)
    : _result_handler(nullptr),
      _result_promise(std::move(result_promise)),
      _start_time(clock)
{
}

ApplyBucketDiffEntryComplete::~ApplyBucketDiffEntryComplete() = default;

void
ApplyBucketDiffEntryComplete::onComplete(std::unique_ptr<spi::Result> result)
{
    if (_result_handler != nullptr) {
        _result_handler->handle(*result);
    }
    _result_promise.set_value(std::make_pair(std::move(result), _start_time.getElapsedTimeAsDouble()));
}

void
ApplyBucketDiffEntryComplete::addResultHandler(const spi::ResultHandler* resultHandler)
{
    assert(_result_handler == nullptr);
    _result_handler = resultHandler;
}

}
