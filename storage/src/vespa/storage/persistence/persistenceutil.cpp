// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceutil.h"
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.util");

namespace storage {
namespace {
    bool isBatchable(api::MessageType::Id id)
    {
        return (id == api::MessageType::PUT_ID ||
                id == api::MessageType::REMOVE_ID ||
                id == api::MessageType::UPDATE_ID ||
                id == api::MessageType::REVERT_ID);
    }

    bool hasBucketInfo(api::MessageType::Id id)
    {
        return (isBatchable(id) ||
                (id == api::MessageType::REMOVELOCATION_ID ||
                 id == api::MessageType::JOINBUCKETS_ID));
    }
    const vespalib::duration WARN_ON_SLOW_OPERATIONS = 5s;
}

MessageTracker::MessageTracker(const framework::MilliSecTimer & timer,
                               const PersistenceUtil & env,
                               MessageSender & replySender,
                               FileStorHandler::BucketLockInterface::SP bucketLock,
                               api::StorageMessage::SP msg)
    : MessageTracker(timer, env, replySender, true, std::move(bucketLock), std::move(msg))
{}
MessageTracker::MessageTracker(const framework::MilliSecTimer & timer,
                               const PersistenceUtil & env,
                               MessageSender & replySender,
                               bool updateBucketInfo,
                               FileStorHandler::BucketLockInterface::SP bucketLock,
                               api::StorageMessage::SP msg)
    : _sendReply(true),
      _updateBucketInfo(updateBucketInfo && hasBucketInfo(msg->getType().getId())),
      _bucketLock(std::move(bucketLock)),
      _msg(std::move(msg)),
      _context(_msg->getPriority(), _msg->getTrace().getLevel()),
      _env(env),
      _replySender(replySender),
      _metric(nullptr),
      _result(api::ReturnCode::OK),
      _timer(timer)
{ }

MessageTracker::UP
MessageTracker::createForTesting(const framework::MilliSecTimer & timer, PersistenceUtil &env, MessageSender &replySender,
                                 FileStorHandler::BucketLockInterface::SP bucketLock, api::StorageMessage::SP msg)
{
    return MessageTracker::UP(new MessageTracker(timer, env, replySender, false, std::move(bucketLock), std::move(msg)));
}

void
MessageTracker::setMetric(FileStorThreadMetrics::Op& metric) {
    metric.count.inc();
    _metric = &metric;
}

MessageTracker::~MessageTracker() = default;

bool MessageTracker::count_result_as_failure() const noexcept {
    // Explicitly don't treat TaS failures as regular failures. These are tracked separately
    // for operations that support TaS conditions.
    if (hasReply() && getReply().getResult().failed()) {
        return (getReply().getResult().getResult() != api::ReturnCode::TEST_AND_SET_CONDITION_FAILED);
    }
    return (getResult().failed()
            && (getResult().getResult() != api::ReturnCode::TEST_AND_SET_CONDITION_FAILED));
}

void
MessageTracker::sendReply() {
    if ( ! _msg->getType().isReply()) {
        generateReply(static_cast<api::StorageCommand &>(*_msg));
    }
    if (count_result_as_failure()) {
        _env._metrics.failedOperations.inc();
    }
    vespalib::duration duration = vespalib::from_s(_timer.getElapsedTimeAsDouble()/1000.0);
    if (duration >= WARN_ON_SLOW_OPERATIONS) {
        LOGBT(warning, _msg->getType().toString(),
              "Slow processing of message %s. Processing time: %1.1f s (>=%1.1f s)",
              _msg->toString().c_str(), vespalib::to_s(duration), vespalib::to_s(WARN_ON_SLOW_OPERATIONS));
    } else {
        LOGBT(spam, _msg->getType().toString(), "Processing time of message %s: %1.1f s",
              _msg->toString(true).c_str(), vespalib::to_s(duration));
    }
    if (hasReply()) {
        getReply().getTrace().addChild(_context.steal_trace());
        if (_updateBucketInfo) {
            if (getReply().getResult().success()) {
                _env.setBucketInfo(*this, _bucketLock->getBucket());
            }
        }
        if (getReply().getResult().success()) {
            _metric->latency.addValue(_timer.getElapsedTimeAsDouble());
        }
        LOG(spam, "Sending reply up: %s %" PRIu64, getReply().toString().c_str(), getReply().getMsgId());
        _replySender.sendReplyDirectly(std::move(_reply));
    } else {
        _msg->getTrace().addChild(_context.steal_trace());
    }
}

bool
MessageTracker::checkForError(const spi::Result& response)
{
    uint32_t code = PersistenceUtil::convertErrorCode(response);

    if (code != 0) {
        fail(code, response.getErrorMessage());
        return false;
    }

    return true;
}

void
MessageTracker::fail(const api::ReturnCode& result)
{
    _result = result;
    LOG(debug, "Failing operation with error: %s", _result.toString().c_str());
}

void
MessageTracker::generateReply(api::StorageCommand& cmd)
{
    if (!_sendReply) {
        return;
    }

    if (!_reply) {
        _reply = cmd.makeReply();
        _reply->setResult(_result);
    }

    if (!_reply->getResult().success()) {
        // TaS failures are tracked separately and explicitly in the put/update/remove paths,
        // so don't double-count them here.
        if (_reply->getResult().getResult() != api::ReturnCode::TEST_AND_SET_CONDITION_FAILED) {
            _metric->failed.inc();
        }
        LOGBP(debug, "Failed to handle command %s: %s",
              cmd.toString().c_str(),
              _result.toString().c_str());
    }
}

PersistenceUtil::PersistenceUtil(const ServiceLayerComponent& component, FileStorHandler& fileStorHandler,
                                 FileStorThreadMetrics& metrics, spi::PersistenceProvider& provider)
    : _component(component),
      _fileStorHandler(fileStorHandler),
      _metrics(metrics),
      _nodeIndex(component.getIndex()),
      _bucketIdFactory(component.getBucketIdFactory()),
      _spi(provider),
      _lastGeneration(0),
      _repos()
{
}

PersistenceUtil::~PersistenceUtil() = default;

void
PersistenceUtil::updateBucketDatabase(const document::Bucket &bucket, const api::BucketInfo& i) const
{
    // Update bucket database
    StorBucketDatabase::WrappedEntry entry(getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(),
                                                                                          "env::updatebucketdb"));
    if (entry.exist()) {
        api::BucketInfo info = i;

        // Don't override last modified unless this is the first bucket
        // info reading.
        if (entry->info.getLastModified() != 0) {
            info.setLastModified(entry->info.getLastModified());
        }
        entry->setBucketInfo(info);
        entry.write();
    } else {
        LOG(debug, "Bucket(%s).getBucketInfo: Bucket does not exist.", bucket.getBucketId().toString().c_str());
    }
}

PersistenceUtil::LockResult
PersistenceUtil::lockAndGetDisk(const document::Bucket &bucket, StorBucketDatabase::Flag flags)
{
    // To lock the bucket, we need to ensure that we don't conflict with
    // bucket disk move command. First we fetch current disk index from
    // bucket DB. When we attempt to lock that lock. And lastly we check
    // the bucket DB again to verify that the bucket is still on that
    // disk after locking it, or we will have to retry on new disk.
    LockResult result;

    while (true) {
        // This function is only called in a context where we require exclusive
        // locking (split/join). Refactor if this no longer the case.
        std::shared_ptr<FileStorHandler::BucketLockInterface> lock(
                _fileStorHandler.lock(bucket, api::LockingRequirements::Exclusive));

        // TODO disks are no longer used in practice, can we safely discard this?
        // Might need it for synchronization purposes if something has taken the
        // disk lock _and_ the bucket lock...?
        StorBucketDatabase::WrappedEntry entry(getBucketDatabase(bucket.getBucketSpace()).get(
                bucket.getBucketId(), "join-lockAndGetDisk-1", flags));
        result.lock = lock;
        return result;
    }
}

void
PersistenceUtil::setBucketInfo(MessageTracker& tracker, const document::Bucket &bucket) const
{
    api::BucketInfo info = getBucketInfo(bucket);

    static_cast<api::BucketInfoReply&>(tracker.getReply()).setBucketInfo(info);

    updateBucketDatabase(bucket, info);
}

api::BucketInfo
PersistenceUtil::getBucketInfo(const document::Bucket &bucket) const
{
    spi::BucketInfoResult response = _spi.getBucketInfo(spi::Bucket(bucket));

    return convertBucketInfo(response.getBucketInfo());
}

api::BucketInfo
PersistenceUtil::convertBucketInfo(const spi::BucketInfo& info)
{
   return api::BucketInfo(info.getChecksum(),
                          info.getDocumentCount(),
                          info.getDocumentSize(),
                          info.getEntryCount(),
                          info.getUsedSize(),
                          info.isReady(),
                          info.isActive(), 0);
}

uint32_t
PersistenceUtil::convertErrorCode(const spi::Result& response)
{
    switch (response.getErrorCode()) {
    case spi::Result::ErrorType::NONE:
        return 0;
    case spi::Result::ErrorType::TIMESTAMP_EXISTS:
        return api::ReturnCode::TIMESTAMP_EXIST;
    case spi::Result::ErrorType::TRANSIENT_ERROR:
    case spi::Result::ErrorType::FATAL_ERROR:
        return mbus::ErrorCode::APP_TRANSIENT_ERROR;
    case spi::Result::ErrorType::RESOURCE_EXHAUSTED:
        return api::ReturnCode::NO_SPACE;
    case spi::Result::ErrorType::PERMANENT_ERROR:
    default:
        return mbus::ErrorCode::APP_FATAL_ERROR;
    }

    return 0;
}

spi::Bucket
PersistenceUtil::getBucket(const document::DocumentId& id, const document::Bucket &bucket) const
{
    document::BucketId docBucket(_bucketIdFactory.getBucketId(id));
    docBucket.setUsedBits(bucket.getBucketId().getUsedBits());
    if (bucket.getBucketId() != docBucket) {
        docBucket = _bucketIdFactory.getBucketId(id);
        throw vespalib::IllegalStateException("Document " + id.toString()
                                              + " (bucket " + docBucket.toString() + ") does not belong in "
                                              + "bucket " + bucket.getBucketId().toString() + ".", VESPA_STRLOC);
    }

    return spi::Bucket(bucket);
}

void
PersistenceUtil::reloadComponent() const {
    // Thread safe as it is only called from the same thread
    while (componentHasChanged()) {
        _lastGeneration = _component.getGeneration();
        _repos = _component.getTypeRepo();
    }
}

} // storage
