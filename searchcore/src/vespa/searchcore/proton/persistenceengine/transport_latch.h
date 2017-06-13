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
class TransportLatch : public FeedToken::ITransport {
private:
    vespalib::CountDownLatch _latch;
    vespalib::Lock           _lock;
    ResultUP                 _result;

public:
    TransportLatch(uint32_t cnt);
    ~TransportLatch();
    virtual void send(mbus::Reply::UP reply,
                      ResultUP result,
                      bool documentWasFound,
                      double latency_ms) override;
    void await() {
        _latch.await();
    }
    const storage::spi::UpdateResult &getUpdateResult() const {
        return dynamic_cast<const storage::spi::UpdateResult &>(*_result);
    }
    const storage::spi::Result &getResult() const {
        return *_result;
    }
    const storage::spi::RemoveResult &getRemoveResult() const {
        return dynamic_cast<const storage::spi::RemoveResult &>(*_result);
    }
    static storage::spi::Result mergeErrorResults(const storage::spi::Result &lhs,
                                                  const storage::spi::Result &rhs);
};

} // namespace proton

