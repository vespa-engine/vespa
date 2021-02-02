// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "asynchandler.h"
#include "persistenceutil.h"
#include "testandsethelper.h"
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/destructor_callbacks.h>

namespace storage {

namespace {

class ResultTask : public vespalib::Executor::Task {
public:
    ResultTask() : _result(), _resultHandler(nullptr) {}

    void setResult(spi::Result::UP result) {
        _result = std::move(result);
    }

    void addResultHandler(const spi::ResultHandler *resultHandler) {
        // Only handles a single handler now,
        // Can be extended if necessary later on
        assert(_resultHandler == nullptr);
        _resultHandler = resultHandler;
    }

    void handle(const spi::Result &result) {
        if (_resultHandler != nullptr) {
            _resultHandler->handle(result);
        }
    }

protected:
    spi::Result::UP _result;
private:
    const spi::ResultHandler *_resultHandler;
};

template<class FunctionType>
class LambdaResultTask : public ResultTask {
public:
    explicit LambdaResultTask(FunctionType &&func)
        : _func(std::move(func))
    {}

    ~LambdaResultTask() override = default;

    void run() override {
        handle(*_result);
        _func(std::move(_result));
    }

private:
    FunctionType _func;
};

template<class FunctionType>
std::unique_ptr<ResultTask>
makeResultTask(FunctionType &&function) {
    return std::make_unique<LambdaResultTask<std::decay_t<FunctionType>>>
            (std::forward<FunctionType>(function));
}

class ResultTaskOperationDone : public spi::OperationComplete {
public:
    ResultTaskOperationDone(vespalib::ISequencedTaskExecutor & executor, document::BucketId bucketId,
                            std::unique_ptr<ResultTask> task)
        : _executor(executor),
          _task(std::move(task)),
          _executorId(executor.getExecutorId(bucketId.getId()))
    {
    }
    void onComplete(spi::Result::UP result) override {
        _task->setResult(std::move(result));
        _executor.executeTask(_executorId, std::move(_task));
    }
    void addResultHandler(const spi::ResultHandler * resultHandler) override {
        _task->addResultHandler(resultHandler);
    }
private:
    vespalib::ISequencedTaskExecutor             & _executor;
    std::unique_ptr<ResultTask>                    _task;
    vespalib::ISequencedTaskExecutor::ExecutorId   _executorId;
};

}
AsyncHandler::AsyncHandler(const PersistenceUtil & env, spi::PersistenceProvider & spi,
                           vespalib::ISequencedTaskExecutor & executor,
                           const document::BucketIdFactory & bucketIdFactory)
    : _env(env),
      _spi(spi),
      _sequencedExecutor(executor),
      _bucketIdFactory(bucketIdFactory)
{}

MessageTracker::UP
AsyncHandler::handleRunTask(RunTaskCommand& cmd, MessageTracker::UP tracker) const {
    auto task = makeResultTask([tracker = std::move(tracker)](spi::Result::UP response) {
        tracker->checkForError(*response);
        tracker->sendReply();
    });
    spi::Bucket bucket(cmd.getBucket());
    auto onDone = std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task));
    cmd.run(bucket, std::make_shared<vespalib::KeepAlive<decltype(onDone)>>(std::move(onDone)));
    return tracker;
}

MessageTracker::UP
AsyncHandler::handlePut(api::PutCommand& cmd, MessageTracker::UP trackerUP) const
{
    MessageTracker & tracker = *trackerUP;
    auto& metrics = _env._metrics.put;
    tracker.setMetric(metrics);
    metrics.request_size.addValue(cmd.getApproxByteSize());

    if (tasConditionExists(cmd) && !tasConditionMatches(cmd, tracker, tracker.context())) {
        // Will also count condition parse failures etc as TaS failures, but
        // those results _will_ increase the error metrics as well.
        metrics.test_and_set_failed.inc();
        return trackerUP;
    }

    spi::Bucket bucket = _env.getBucket(cmd.getDocumentId(), cmd.getBucket());
    auto task = makeResultTask([tracker = std::move(trackerUP)](spi::Result::UP response) {
        tracker->checkForError(*response);
        tracker->sendReply();
    });
    _spi.putAsync(bucket, spi::Timestamp(cmd.getTimestamp()), std::move(cmd.getDocument()), tracker.context(),
                  std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task)));

    return trackerUP;
}

MessageTracker::UP
AsyncHandler::handleUpdate(api::UpdateCommand& cmd, MessageTracker::UP trackerUP) const
{
    MessageTracker & tracker = *trackerUP;
    auto& metrics = _env._metrics.update;
    tracker.setMetric(metrics);
    metrics.request_size.addValue(cmd.getApproxByteSize());

    if (tasConditionExists(cmd) && !tasConditionMatches(cmd, tracker, tracker.context(), cmd.getUpdate()->getCreateIfNonExistent())) {
        metrics.test_and_set_failed.inc();
        return trackerUP;
    }

    spi::Bucket bucket = _env.getBucket(cmd.getDocumentId(), cmd.getBucket());

    // Note that the &cmd capture is OK since its lifetime is guaranteed by the tracker
    auto task = makeResultTask([&cmd, tracker = std::move(trackerUP)](spi::Result::UP responseUP) {
        auto & response = dynamic_cast<const spi::UpdateResult &>(*responseUP);
        if (tracker->checkForError(response)) {
            auto reply = std::make_shared<api::UpdateReply>(cmd);
            reply->setOldTimestamp(response.getExistingTimestamp());
            tracker->setReply(std::move(reply));
        }
        tracker->sendReply();
    });
    _spi.updateAsync(bucket, spi::Timestamp(cmd.getTimestamp()), std::move(cmd.getUpdate()), tracker.context(),
                     std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task)));
    return trackerUP;
}

MessageTracker::UP
AsyncHandler::handleRemove(api::RemoveCommand& cmd, MessageTracker::UP trackerUP) const
{
    MessageTracker & tracker = *trackerUP;
    auto& metrics = _env._metrics.remove;
    tracker.setMetric(metrics);
    metrics.request_size.addValue(cmd.getApproxByteSize());

    if (tasConditionExists(cmd) && !tasConditionMatches(cmd, tracker, tracker.context())) {
        metrics.test_and_set_failed.inc();
        return trackerUP;
    }

    spi::Bucket bucket = _env.getBucket(cmd.getDocumentId(), cmd.getBucket());

    // Note that the &cmd capture is OK since its lifetime is guaranteed by the tracker
    auto task = makeResultTask([&metrics, &cmd, tracker = std::move(trackerUP)](spi::Result::UP responseUP) {
        auto & response = dynamic_cast<const spi::RemoveResult &>(*responseUP);
        if (tracker->checkForError(response)) {
            tracker->setReply(std::make_shared<api::RemoveReply>(cmd, response.wasFound() ? cmd.getTimestamp() : 0));
        }
        if (!response.wasFound()) {
            metrics.notFound.inc();
        }
        tracker->sendReply();
    });
    _spi.removeIfFoundAsync(bucket, spi::Timestamp(cmd.getTimestamp()), cmd.getDocumentId(), tracker.context(),
                            std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task)));
    return trackerUP;
}

bool
AsyncHandler::is_async_message(api::MessageType::Id type_id) noexcept
{
    switch (type_id) {
        case api::MessageType::PUT_ID:
        case api::MessageType::UPDATE_ID:
        case api::MessageType::REMOVE_ID:
            return true;
        default:
            return false;
    }
}

bool
AsyncHandler::tasConditionExists(const api::TestAndSetCommand & cmd) {
    return cmd.getCondition().isPresent();
}

bool
AsyncHandler::tasConditionMatches(const api::TestAndSetCommand & cmd, MessageTracker & tracker,
                                  spi::Context & context, bool missingDocumentImpliesMatch) const {
    try {
        TestAndSetHelper helper(_env, _spi, _bucketIdFactory, cmd, missingDocumentImpliesMatch);

        auto code = helper.retrieveAndMatch(context);
        if (code.failed()) {
            tracker.fail(code.getResult(), code.getMessage());
            return false;
        }
    } catch (const TestAndSetException & e) {
        auto code = e.getCode();
        tracker.fail(code.getResult(), code.getMessage());
        return false;
    }

    return true;
}

}
