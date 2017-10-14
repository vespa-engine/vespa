// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/result.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/vespalib/util/sequence.h>

namespace proton {

/**
 * Implementation of FeedToken::ITransport for handling the async reply for an operation.
 * Uses an internal count down latch to keep track the number of outstanding replies.
 */
class TransportLatch : public feedtoken::ITransport {
private:
    using Result = storage::spi::Result;
    using UpdateResult = storage::spi::UpdateResult;
    using RemoveResult = storage::spi::RemoveResult;
    vespalib::CountDownLatch _latch;
    vespalib::Lock           _lock;
    ResultUP                 _result;

public:
    TransportLatch(uint32_t cnt);
    ~TransportLatch();
    void send(ResultUP result, bool documentWasFound) override;
    void await() {
        _latch.await();
    }
    const UpdateResult &getUpdateResult() const {
        return dynamic_cast<const UpdateResult &>(*_result);
    }
    const Result &getResult() const {
        return *_result;
    }
    const RemoveResult &getRemoveResult() const {
        return dynamic_cast<const RemoveResult &>(*_result);
    }
    static Result mergeErrorResults(const Result &lhs, const Result &rhs);
};

} // namespace proton

