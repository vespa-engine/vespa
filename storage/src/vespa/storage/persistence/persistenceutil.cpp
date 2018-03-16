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
}

MessageTracker::MessageTracker(FileStorThreadMetrics::Op& metric,
                               framework::Clock& clock)
    : _sendReply(true),
      _metric(metric),
      _result(api::ReturnCode::OK),
      _timer(clock)
{
    ++_metric.count;
}

MessageTracker::~MessageTracker()
{
    if (_reply.get() && _reply->getResult().success()) {
        _metric.latency.addValue(_timer.getElapsedTimeAsDouble());
    }
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

    if (!_reply.get()) {
        _reply.reset(cmd.makeReply().release());
        _reply->setResult(_result);
    }

    if (!_reply->getResult().success()) {
        ++_metric.failed;
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
        uint16_t partition,
        spi::PersistenceProvider& provider)
    : _config(*config::ConfigGetter<vespa::config::content::StorFilestorConfig>::getConfig(configUri.getConfigId(), configUri.getContext())),
      _compReg(compReg),
      _component(compReg, generateName(this)),
      _fileStorHandler(fileStorHandler),
      _partition(partition),
      _nodeIndex(_component.getIndex()),
      _metrics(metrics),
      _bucketFactory(_component.getBucketIdFactory()),
      _repo(_component.getTypeRepo()),
      _pauseHandler(),
      _spi(provider)
{
}

PersistenceUtil::~PersistenceUtil() { }

void
PersistenceUtil::updateBucketDatabase(const document::Bucket &bucket, const api::BucketInfo& i)
{
    // Update bucket database
    StorBucketDatabase::WrappedEntry entry(getBucketDatabase(bucket.getBucketSpace()).get(
                                                   bucket.getBucketId(),
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
        LOG(debug,
            "Bucket(%s).getBucketInfo: Bucket does not exist.",
            bucket.getBucketId().toString().c_str());
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
    result.disk = getPreferredAvailableDisk(bucket);

    while (true) {
        std::shared_ptr<FileStorHandler::BucketLockInterface> lock(
                _fileStorHandler.lock(bucket, result.disk));

        StorBucketDatabase::WrappedEntry entry(getBucketDatabase(bucket.getBucketSpace()).get(
                bucket.getBucketId(), "join-lockAndGetDisk-1", flags));
        if (entry.exist() && entry->disk != result.disk) {
            result.disk = entry->disk;
            continue;
        }

        result.lock = lock;
        return result;
    }
}

void
PersistenceUtil::setBucketInfo(MessageTracker& tracker, const document::Bucket &bucket)
{
    api::BucketInfo info = getBucketInfo(bucket, _partition);

    static_cast<api::BucketInfoReply&>(*tracker.getReply()).
        setBucketInfo(info);

    updateBucketDatabase(bucket, info);
}

api::BucketInfo
PersistenceUtil::getBucketInfo(const document::Bucket &bucket, int disk) const
{
    if (disk == -1) {
        disk = _partition;
    }

    spi::BucketInfoResult response =
        _spi.getBucketInfo(spi::Bucket(bucket, spi::PartitionId(disk)));

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
    case spi::Result::NONE:
        return 0;
    case spi::Result::TIMESTAMP_EXISTS:
        return api::ReturnCode::TIMESTAMP_EXIST;
    case spi::Result::TRANSIENT_ERROR:
    case spi::Result::FATAL_ERROR:
        return mbus::ErrorCode::APP_TRANSIENT_ERROR;
    case spi::Result::RESOURCE_EXHAUSTED:
        return api::ReturnCode::NO_SPACE;
    case spi::Result::PERMANENT_ERROR:
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
