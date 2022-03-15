// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resulthandler.h"

namespace proton::test {

GenericResultHandler::~GenericResultHandler() = default;

void
GenericResultHandler::handle(const storage::spi::Result &result) {
    _result = std::make_unique<storage::spi::Result>(result);
}

BucketInfoResultHandler::~BucketInfoResultHandler() = default;
void
BucketInfoResultHandler::handle(const storage::spi::BucketInfoResult &result) {
    _result = std::make_unique<storage::spi::BucketInfoResult>(result);
}

BucketIdListResultHandler::~BucketIdListResultHandler() = default;

void
BucketIdListResultHandler::handle(storage::spi::BucketIdListResult result) {
    _result = std::make_unique<storage::spi::BucketIdListResult>(std::move(result));
}

}
