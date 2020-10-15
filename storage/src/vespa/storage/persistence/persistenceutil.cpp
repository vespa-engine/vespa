// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceutil.h"
#include <vespa/config/config.h>
#include <vespa/config/helper/configgetter.hpp>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.util");

namespace storage {
namespace {
    std::string generateName(void* p) {
        std::ostringstream ost;
        ost << "PersistenceUtil(" << p << ")";
        return ost.str();
    }

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

MessageTracker::MessageTracker(PersistenceUtil & env,
                               MessageSender & replySender,
                               FileStorHandler::BucketLockInterface::SP bucketLock,
                               api::StorageMessage::SP msg)
    : MessageTracker(env, replySender, true, std::move(bucketLock), std::move(msg))
{}
MessageTracker::MessageTracker(PersistenceUtil & env,
                               MessageSender & replySender,
                               bool updateBucketInfo,
                               FileStorHandler::BucketLockInterface::SP bucketLock,
                               api::StorageMessage::SP msg)
    : _sendReply(true),
      _updateBucketInfo(updateBucketInfo && hasBucketInfo(msg->getType().getId())),
      _bucketLock(std::move(bucketLock)),
      _msg(std::move(msg)),
      _context(_msg->getLoadType(), _msg->getPriority(), _msg->getTrace().getLevel()),
      _env(env),
      _replySender(replySender),
      _metric(nullptr),
      _result(api::ReturnCode::OK),
      _timer(_env._component.getClock())
{ }

MessageTracker::UP
MessageTracker::createForTesting(PersistenceUtil &env, MessageSender &replySender, FileStorHandler::BucketLockInterface::SP bucketLock, api::StorageMessage::SP msg) {
    return MessageTracker::UP(new MessageTracker(env, replySender, false, std::move(bucketLock), std::move(msg)));
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
        if ( ! _context.getTrace().getRoot().isEmpty()) {
            getReply().getTrace().getRoot().addChild(_context.getTrace().getRoot());
        }
        if (_updateBucketInfo) {
            if (getReply().getResult().success()) {
                _env.setBucketInfo(*this, _bucketLock->getBucket());
            }
        }
        if (getReply().getResult().success()) {
            _metric->latency.addValue(_timer.getElapsedTimeAsDouble());
        }
        LOG(spam, "Sending reply up: %s %" PRIu64,
            getReply().toString().c_str(), getReply().getMsgId());
        _replySender.sendReplyDirectly(std::move(_reply));
    } else {
        if ( ! _context.getTrace().getRoot().isEmpty()) {
            _msg->getTrace().getRoot().addChild(_context.getTrace().getRoot());
        }
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
MessageTracker::fail(const ReturnCode& result)
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

PersistenceUtil::PersistenceUtil(
        const config::ConfigUri & configUri,
        ServiceLayerComponentRegister& compReg,
        FileStorHandler& fileStorHandler,
        FileStorThreadMetrics& metrics,
        spi::PersistenceProvider& provider)
    : _config(*config::ConfigGetter<vespa::config::content::StorFilestorConfig>::getConfig(configUri.getConfigId(), configUri.getContext())),
      _compReg(compReg),
      _component(compReg, generateName(this)),
      _fileStorHandler(fileStorHandler),
      _nodeIndex(_component.getIndex()),
      _metrics(metrics),
      _bucketFactory(_component.getBucketIdFactory()),
      _repo(_component.getTypeRepo()->documentTypeRepo),
      _spi(provider)
{
}

PersistenceUtil::~PersistenceUtil() = default;

void
PersistenceUtil::updateBucketDatabase(const document::Bucket &bucket, const api::BucketInfo& i)
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

uint16_t
PersistenceUtil::getPreferredAvailableDisk(const document::Bucket &bucket) const
{
    return _component.getPreferredAvailablePartition(bucket);
}

PersistenceUtil::LockResult
PersistenceUtil::lockAndGetDisk(const document::Bucket &bucket,
                                StorBucketDatabase::Flag flags)
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
PersistenceUtil::setBucketInfo(MessageTracker& tracker, const document::Bucket &bucket)
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
PersistenceUtil::convertBucketInfo(const spi::BucketInfo& info) const
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

void
PersistenceUtil::shutdown(const std::string& reason)
{
    _component.requestShutdown(reason);
}

} // storage
