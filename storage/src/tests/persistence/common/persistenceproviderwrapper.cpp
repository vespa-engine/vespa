// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceproviderwrapper.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <sstream>

#define LOG_SPI(ops) \
    { \
        std::ostringstream logStream; \
        logStream << ops; \
        Guard guard(_lock); \
        _log.push_back(logStream.str()); \
    }

#define CHECK_ERROR(className, failType) \
    { \
        Guard guard(_lock); \
        if (_result.getErrorCode() != spi::Result::ErrorType::NONE && (_failureMask & (failType))) { \
            return className(_result.getErrorCode(), _result.getErrorMessage()); \
        } \
    }

#define CHECK_ERROR_ASYNC(className, failType, onError) \
    { \
        Guard guard(_lock);                             \
        if (_result.getErrorCode() != spi::Result::ErrorType::NONE && (_failureMask & (failType))) { \
            onError->onComplete(std::make_unique<className>(_result.getErrorCode(), _result.getErrorMessage())); \
            return; \
        } \
    }

namespace storage {

namespace {

const char*
includedVersionsToString(spi::IncludedVersions versions)
{
    switch (versions) {
    case spi::NEWEST_DOCUMENT_ONLY:
        return "NEWEST_DOCUMENT_ONLY";
    case spi::NEWEST_DOCUMENT_OR_REMOVE:
        return "NEWEST_DOCUMENT_OR_REMOVE";
    case spi::ALL_VERSIONS:
        return "ALL_VERSIONS";
    }
    return "!!UNKNOWN!!";
}

} // anon namespace

PersistenceProviderWrapper::PersistenceProviderWrapper(spi::PersistenceProvider& spi)
    : _spi(spi),
      _result(spi::Result(spi::Result::ErrorType::NONE, "")),
      _lock(),
      _log(),
      _failureMask(0)
{ }
PersistenceProviderWrapper::~PersistenceProviderWrapper() = default;


std::string
PersistenceProviderWrapper::toString() const
{
    std::ostringstream ss;
    Guard guard(_lock);
    for (size_t i = 0; i < _log.size(); ++i) {
        ss << _log[i] << "\n";
    }
    return ss.str();
}

spi::BucketIdListResult
PersistenceProviderWrapper::listBuckets(BucketSpace bucketSpace) const
{
    LOG_SPI("listBuckets(" << bucketSpace.getId() << ")");
    CHECK_ERROR(spi::BucketIdListResult, FAIL_LIST_BUCKETS);
    return _spi.listBuckets(bucketSpace);
}

void
PersistenceProviderWrapper::createBucketAsync(const spi::Bucket& bucket, spi::OperationComplete::UP onComplete) noexcept
{
    LOG_SPI("createBucket(" << bucket << ")");
    CHECK_ERROR_ASYNC(spi::Result, FAIL_CREATE_BUCKET, onComplete);
    return _spi.createBucketAsync(bucket, std::move(onComplete));
}

spi::BucketInfoResult
PersistenceProviderWrapper::getBucketInfo(const spi::Bucket& bucket) const
{
    LOG_SPI("getBucketInfo(" << bucket << ")");
    CHECK_ERROR(spi::BucketInfoResult, FAIL_BUCKET_INFO);
    return _spi.getBucketInfo(bucket);
}

void
PersistenceProviderWrapper::putAsync(const spi::Bucket& bucket, spi::Timestamp timestamp, document::Document::SP doc,
                                     spi::OperationComplete::UP onComplete)
{
    LOG_SPI("put(" << bucket << ", " << timestamp << ", " << doc->getId() << ")");
    CHECK_ERROR_ASYNC(spi::Result, FAIL_PUT, onComplete);
    _spi.putAsync(bucket, timestamp, std::move(doc), std::move(onComplete));
}

void
PersistenceProviderWrapper::removeAsync(const spi::Bucket& bucket,  std::vector<spi::IdAndTimestamp> ids,
                                        spi::OperationComplete::UP onComplete)
{
    for (const auto & stampedId : ids) {
        LOG_SPI("remove(" << bucket << ", " << stampedId.timestamp << ", " << stampedId.id << ")");
    }
    CHECK_ERROR_ASYNC(spi::RemoveResult, FAIL_REMOVE, onComplete);
    _spi.removeAsync(bucket, std::move(ids), std::move(onComplete));
}

void
PersistenceProviderWrapper::removeIfFoundAsync(const spi::Bucket& bucket, spi::Timestamp timestamp, const spi::DocumentId& id,
                                               spi::OperationComplete::UP onComplete)
{
    LOG_SPI("removeIfFound(" << bucket << ", " << timestamp << ", " << id << ")");
    CHECK_ERROR_ASYNC(spi::RemoveResult, FAIL_REMOVE_IF_FOUND, onComplete);
    _spi.removeIfFoundAsync(bucket, timestamp, id, std::move(onComplete));
}

void
PersistenceProviderWrapper::updateAsync(const spi::Bucket& bucket, spi::Timestamp timestamp, document::DocumentUpdate::SP upd,
                                        spi::OperationComplete::UP onComplete)
{
    LOG_SPI("update(" << bucket << ", " << timestamp << ", " << upd->getId() << ")");
    CHECK_ERROR_ASYNC(spi::UpdateResult, FAIL_UPDATE, onComplete);
    _spi.updateAsync(bucket, timestamp, std::move(upd), std::move(onComplete));
}

spi::GetResult
PersistenceProviderWrapper::get(const spi::Bucket& bucket, const document::FieldSet& fieldSet,
                                const spi::DocumentId& id, spi::Context& context) const
{
    LOG_SPI("get(" << bucket << ", " << id << ")");
    CHECK_ERROR(spi::GetResult, FAIL_GET);
    return _spi.get(bucket, fieldSet, id, context);
}

spi::CreateIteratorResult
PersistenceProviderWrapper::createIterator(const spi::Bucket &bucket, FieldSetSP fields, const spi::Selection &sel,
                                           spi::IncludedVersions versions, spi::Context &context)
{
    // TODO: proper printing of FieldSet and Selection

    LOG_SPI("createIterator(" << bucket << ", "
            << includedVersionsToString(versions) << ")");
    CHECK_ERROR(spi::CreateIteratorResult, FAIL_CREATE_ITERATOR);
    return _spi.createIterator(bucket, fields, sel, versions, context);
}

spi::IterateResult
PersistenceProviderWrapper::iterate(spi::IteratorId iterId, uint64_t maxByteSize) const
{
    LOG_SPI("iterate(" << uint64_t(iterId) << ", " << maxByteSize << ")");
    CHECK_ERROR(spi::IterateResult, FAIL_ITERATE);
    return _spi.iterate(iterId, maxByteSize);
}

spi::Result
PersistenceProviderWrapper::destroyIterator(spi::IteratorId iterId)
{
    LOG_SPI("destroyIterator(" << uint64_t(iterId) << ")");
    CHECK_ERROR(spi::Result, FAIL_DESTROY_ITERATOR);
    return _spi.destroyIterator(iterId);
}

void
PersistenceProviderWrapper::deleteBucketAsync(const spi::Bucket& bucket, spi::OperationComplete::UP operationComplete) noexcept
{
    LOG_SPI("deleteBucket(" << bucket << ")");
    CHECK_ERROR_ASYNC(spi::Result, FAIL_DELETE_BUCKET, operationComplete);
    _spi.deleteBucketAsync(bucket, std::move(operationComplete));
}

spi::Result
PersistenceProviderWrapper::split(const spi::Bucket& source, const spi::Bucket& target1, const spi::Bucket& target2)
{
    LOG_SPI("split(" << source << ", " << target1 << ", " << target2 << ")");
    CHECK_ERROR(spi::Result, FAIL_SPLIT);
    return _spi.split(source, target1, target2);
}

spi::Result
PersistenceProviderWrapper::join(const spi::Bucket& source1, const spi::Bucket& source2, const spi::Bucket& target)
{
    LOG_SPI("join(" << source1 << ", " << source2 << ", " << target << ")");
    CHECK_ERROR(spi::Result, FAIL_JOIN);
    return _spi.join(source1, source2, target);
}

std::unique_ptr<vespalib::IDestructorCallback>
PersistenceProviderWrapper::register_resource_usage_listener(spi::IResourceUsageListener& listener)
{
    return _spi.register_resource_usage_listener(listener);
}

std::unique_ptr<vespalib::IDestructorCallback>
PersistenceProviderWrapper::register_executor(std::shared_ptr<spi::BucketExecutor> executor)
{
    return _spi.register_executor(std::move(executor));
}

spi::Result
PersistenceProviderWrapper::removeEntry(const spi::Bucket& bucket, spi::Timestamp timestamp)
{
    LOG_SPI("revert(" << bucket << ", " << timestamp << ")");
    CHECK_ERROR(spi::Result, FAIL_REVERT);
    return _spi.removeEntry(bucket, timestamp);
}

spi::Result
PersistenceProviderWrapper::initialize() {
    return _spi.initialize();
}

spi::BucketIdListResult
PersistenceProviderWrapper::getModifiedBuckets(spi::PersistenceProvider::BucketSpace bucketSpace) const {
    return _spi.getModifiedBuckets(bucketSpace);
}

spi::Result
PersistenceProviderWrapper::setClusterState(spi::PersistenceProvider::BucketSpace bucketSpace, const spi::ClusterState &state) {
    return _spi.setClusterState(bucketSpace, state);
}

void
PersistenceProviderWrapper::setActiveStateAsync(const spi::Bucket &bucket, spi::BucketInfo::ActiveState state,
                                                spi::OperationComplete::UP onComplete)
{
    _spi.setActiveStateAsync(bucket, state, std::move(onComplete));
}

}
