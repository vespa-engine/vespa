// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/result.h>
#include <vespa/persistence/spi/operationcomplete.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/vespalib/util/sequence.h>
#include <vespa/vespalib/util/count_down_latch.h>
#include <mutex>

namespace proton {

/**
 * Base implementation for merging results from multiple sources.
 */

class TransportMerger : public feedtoken::ITransport {
public:
    using Result = storage::spi::Result;
    static Result mergeErrorResults(const Result &lhs, const Result &rhs);
protected:
    TransportMerger(bool needLocking);
    ~TransportMerger() override;
    void mergeResult(ResultUP result, bool documentWasFound);
    virtual void completeIfDone() { } // Called with lock held if necessary on every merge
    ResultUP  _result;

private:
    void mergeWithLock(ResultUP result, bool documentWasFound);
    std::unique_ptr<std::mutex> _lock;
};

/**
 * Implementation of FeedToken::ITransport for handling the async reply for an operation.
 * Uses an internal count down latch to keep track the number of outstanding replies.
 */
class TransportLatch : public TransportMerger {
private:
    using UpdateResult = storage::spi::UpdateResult;
    using RemoveResult = storage::spi::RemoveResult;
    vespalib::CountDownLatch _latch;

public:
    TransportLatch(uint32_t cnt);
    ~TransportLatch() override;
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

};

/**
 * Implementation of FeedToken::ITransport for async handling of the async reply for an operation.
 * Uses an internal count to keep track the number of the outstanding replies.
 */
class AsyncTranportContext : public TransportMerger {
private:
    using Result = storage::spi::Result;
    using OperationComplete = storage::spi::OperationComplete;

    int                   _countDown;
    OperationComplete::UP _onComplete;
    void completeIfDone() override;
public:
    AsyncTranportContext(uint32_t cnt, OperationComplete::UP);
    ~AsyncTranportContext() override;
    void send(ResultUP result, bool documentWasFound) override;
};

} // namespace proton

