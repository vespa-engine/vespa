// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "asynchandler.h"
#include "persistenceutil.h"
#include "testandsethelper.h"
#include "bucketownershipnotifier.h"
#include "bucketprocessor.h"
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/persistence/spi/catchresult.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".storage.persistence.asynchandler");

using vespalib::CpuUsage;
using vespalib::make_string_short::fmt;
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
    ~ResultTaskOperationDone() override;
    void onComplete(spi::Result::UP result) noexcept override {
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

ResultTaskOperationDone::~ResultTaskOperationDone() = default;

bool
bucketStatesAreSemanticallyEqual(const api::BucketInfo& a, const api::BucketInfo& b) {
    // Don't check document sizes, as background moving of documents in Proton
    // may trigger a change in size without any mutations taking place. This will
    // only take place when a document being moved was fed _prior_ to the change
    // where Proton starts reporting actual document sizes, and will eventually
    // converge to a stable value. But for now, ignore it to prevent false positive
    // error logs and non-deleted buckets.
    return ((a.getChecksum() == b.getChecksum()) && (a.getDocumentCount() == b.getDocumentCount()));
}

class UnrevertableRemoveEntryProcessor : public BucketProcessor::EntryProcessor {
public:
    using DocumentIdsAndTimeStamps = std::vector<spi::IdAndTimestamp>;
    UnrevertableRemoveEntryProcessor(DocumentIdsAndTimeStamps & to_remove)
        : _to_remove(to_remove)
    {}

    void process(spi::DocEntry& entry) override {
        _to_remove.emplace_back(*entry.getDocumentId(), entry.getTimestamp());
    }
private:
    DocumentIdsAndTimeStamps & _to_remove;
};

}

AsyncHandler::AsyncHandler(const PersistenceUtil & env, spi::PersistenceProvider & spi,
                           BucketOwnershipNotifier &bucketOwnershipNotifier,
                           vespalib::ISequencedTaskExecutor & executor,
                           const document::BucketIdFactory & bucketIdFactory)
    : _env(env),
      _spi(spi),
      _bucketOwnershipNotifier(bucketOwnershipNotifier),
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

    if (tasConditionExists(cmd) && !tasConditionMatches(cmd, tracker, tracker.context(), cmd.get_create_if_non_existent())) {
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
    _spi.putAsync(bucket, spi::Timestamp(cmd.getTimestamp()), cmd.getDocument(),
                  std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task)));

    return trackerUP;
}

MessageTracker::UP
AsyncHandler::handleCreateBucket(api::CreateBucketCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.createBuckets);
    LOG(debug, "CreateBucket(%s)", cmd.getBucketId().toString().c_str());
    if (_env._fileStorHandler.isMerging(cmd.getBucket())) {
        LOG(warning, "Bucket %s was merging at create time. Unexpected.", cmd.getBucketId().toString().c_str());
    }
    spi::Bucket bucket(cmd.getBucket());
    auto task = makeResultTask([tracker = std::move(tracker)](spi::Result::UP ignored) mutable {
        // TODO Even if an non OK response can not be handled sanely we might probably log a message, or increment a metric
        (void) ignored;
        tracker->sendReply();
    });

    if (cmd.getActive()) {
        _spi.createBucketAsync(bucket, std::make_unique<spi::NoopOperationComplete>());
        _spi.setActiveStateAsync(bucket, spi::BucketInfo::ACTIVE, std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, bucket, std::move(task)));
    } else {
        _spi.createBucketAsync(bucket, std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, bucket, std::move(task)));
    }

    return tracker;
}

MessageTracker::UP
AsyncHandler::handleDeleteBucket(api::DeleteBucketCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.deleteBuckets);
    LOG(debug, "DeletingBucket(%s)", cmd.getBucketId().toString().c_str());
    if (_env._fileStorHandler.isMerging(cmd.getBucket())) {
        _env._fileStorHandler.clearMergeStatus(cmd.getBucket(),
                                               api::ReturnCode(api::ReturnCode::ABORTED, "Bucket was deleted during the merge"));
    }
    spi::Bucket bucket(cmd.getBucket());
    if (!checkProviderBucketInfoMatches(bucket, cmd.getBucketInfo())) {
        return tracker;
    }

    auto task = makeResultTask([this, tracker = std::move(tracker), bucket=cmd.getBucket()](spi::Result::UP ignored) {
        // TODO Even if an non OK response can not be handled sanely we might probably log a message, or increment a metric
        (void) ignored;
        StorBucketDatabase &db(_env.getBucketDatabase(bucket.getBucketSpace()));
        StorBucketDatabase::WrappedEntry entry = db.get(bucket.getBucketId(), "onDeleteBucket");
        if (entry.exist() && entry->getMetaCount() > 0) {
            LOG(debug, "onDeleteBucket(%s): Bucket DB entry existed. Likely "
                       "active operation when delete bucket was queued. "
                       "Updating bucket database to keep it in sync with file. "
                       "Cannot delete bucket from bucket database at this "
                       "point, as it can have been intentionally recreated "
                       "after delete bucket had been sent",
                bucket.getBucketId().toString().c_str());
            api::BucketInfo info(0, 0, 0);
            // Only set document counts/size; retain ready/active state.
            info.setReady(entry->getBucketInfo().isReady());
            info.setActive(entry->getBucketInfo().isActive());

            entry->setBucketInfo(info);
            entry.write();
        }
        tracker->sendReply();
    });
    _spi.deleteBucketAsync(bucket, std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task)));
    return tracker;
}

MessageTracker::UP
AsyncHandler::handleSetBucketState(api::SetBucketStateCommand& cmd, MessageTracker::UP trackerUP) const
{
    trackerUP->setMetric(_env._metrics.setBucketStates);

    //LOG(debug, "handleSetBucketState(): %s", cmd.toString().c_str());
    spi::Bucket bucket(cmd.getBucket());
    bool shouldBeActive(cmd.getState() == api::SetBucketStateCommand::ACTIVE);
    spi::BucketInfo::ActiveState newState(shouldBeActive ? spi::BucketInfo::ACTIVE : spi::BucketInfo::NOT_ACTIVE);

    auto task = makeResultTask([this, &cmd, newState, tracker = std::move(trackerUP), bucket,
                                notifyGuard = std::make_unique<NotificationGuard>(_bucketOwnershipNotifier)](spi::Result::UP response) mutable {
        if (tracker->checkForError(*response)) {
            StorBucketDatabase &db(_env.getBucketDatabase(bucket.getBucketSpace()));
            StorBucketDatabase::WrappedEntry entry = db.get(bucket.getBucketId(),"handleSetBucketState");
            if (entry.exist()) {
                entry->info.setActive(newState == spi::BucketInfo::ACTIVE);
                notifyGuard->notifyIfOwnershipChanged(cmd.getBucket(), cmd.getSourceIndex(), entry->info);
                entry.write();
            } else {
                LOG(warning, "Got OK setCurrentState result from provider for %s, "
                             "but bucket has disappeared from service layer database",
                    cmd.getBucketId().toString().c_str());
            }
            tracker->setReply(std::make_shared<api::SetBucketStateReply>(cmd));
        }
        tracker->sendReply();
    });
    _spi.setActiveStateAsync(bucket, newState, std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task)));
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
    _spi.updateAsync(bucket, spi::Timestamp(cmd.getTimestamp()), cmd.getUpdate(),
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
    _spi.removeIfFoundAsync(bucket, spi::Timestamp(cmd.getTimestamp()), cmd.getDocumentId(),
                            std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task)));
    return trackerUP;
}

bool
AsyncHandler::is_async_unconditional_message(const api::StorageMessage & cmd) noexcept
{
    switch (cmd.getType().getId()) {
        case api::MessageType::PUT_ID:
        case api::MessageType::UPDATE_ID:
        case api::MessageType::REMOVE_ID:
            return ! cmd.hasTestAndSetCondition();
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
        TestAndSetHelper helper(_env, _spi, _bucketIdFactory,
                                cmd.getCondition(),
                                cmd.getBucket(), cmd.getDocumentId(),
                                cmd.getDocumentType(),
                                missingDocumentImpliesMatch);

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

bool
AsyncHandler::checkProviderBucketInfoMatches(const spi::Bucket& bucket, const api::BucketInfo& info) const
{
    spi::BucketInfoResult result(_spi.getBucketInfo(bucket));
    if (result.hasError()) {
        LOG(error, "getBucketInfo(%s) failed before deleting bucket; got error '%s'",
            bucket.toString().c_str(), result.getErrorMessage().c_str());
        return false;
    }
    api::BucketInfo providerInfo(PersistenceUtil::convertBucketInfo(result.getBucketInfo()));
    // Don't check meta fields or active/ready fields since these are not
    // that important and ready may change under the hood in a race with
    // getModifiedBuckets(). If bucket is empty it means it has already
    // been deleted by a racing split/join.
    if (!bucketStatesAreSemanticallyEqual(info, providerInfo) && !providerInfo.empty()) {
        LOG(error,
            "Service layer bucket database and provider out of sync before "
            "deleting bucket %s! Service layer db had %s while provider says "
            "bucket has %s. Deletion has been rejected to ensure data is not "
            "lost, but bucket may remain out of sync until service has been "
            "restarted.",
            bucket.toString().c_str(), info.toString().c_str(), providerInfo.toString().c_str());
        return false;
    }
    return true;
}

MessageTracker::UP
AsyncHandler::handleRemoveLocation(api::RemoveLocationCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.removeLocation);

    spi::Bucket bucket(cmd.getBucket());
    const bool is_legacy = (!cmd.only_enumerate_docs() && cmd.explicit_remove_set().empty());
    std::vector<spi::IdAndTimestamp> to_remove;

    LOG(debug, "RemoveLocation(%s): using selection '%s' (enumerate only: %s, remove set size: %zu)",
        bucket.toString().c_str(),
        cmd.getDocumentSelection().c_str(),
        (cmd.only_enumerate_docs() ? "yes" : "no"),
        cmd.explicit_remove_set().size());

    if (is_legacy || cmd.only_enumerate_docs()) {
        UnrevertableRemoveEntryProcessor processor(to_remove);
        {
            auto usage = vespalib::CpuUsage::use(CpuUsage::Category::READ);
            BucketProcessor::iterateAll(_spi, bucket, cmd.getDocumentSelection(),
                                        std::make_shared<document::DocIdOnly>(),
                                        processor, spi::NEWEST_DOCUMENT_ONLY, tracker->context());
        }
        if (!is_legacy) {
            LOG(debug, "RemoveLocation(%s): returning 1st phase results with %zu entries",
                bucket.toString().c_str(), to_remove.size());
            auto reply = std::make_shared<api::RemoveLocationReply>(cmd, 0); // No docs removed yet
            reply->set_selection_matches(std::move(to_remove));
            tracker->setReply(std::move(reply));
            return tracker;
        }
    } else {
        to_remove = cmd.steal_explicit_remove_set();
    }

    auto task = makeResultTask([&cmd, tracker = std::move(tracker), removed = to_remove.size()](spi::Result::UP response) {
        tracker->checkForError(*response);
        tracker->setReply(std::make_shared<api::RemoveLocationReply>(cmd, removed));
        tracker->sendReply();
    });

    // In the case where a _newer_ mutation exists for a given entry in to_remove, it will be ignored
    // (with no tombstone added) since we only preserve the newest operation for a document.
    _spi.removeAsync(bucket, std::move(to_remove),
                     std::make_unique<ResultTaskOperationDone>(_sequencedExecutor, cmd.getBucketId(), std::move(task)));

    return tracker;
}

}
