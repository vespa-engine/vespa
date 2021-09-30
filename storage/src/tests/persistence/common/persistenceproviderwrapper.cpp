// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

spi::Result
PersistenceProviderWrapper::createBucket(const spi::Bucket& bucket, spi::Context& context)
{
    LOG_SPI("createBucket(" << bucket << ")");
    CHECK_ERROR(spi::Result, FAIL_CREATE_BUCKET);
    return _spi.createBucket(bucket, context);
}

spi::BucketInfoResult
PersistenceProviderWrapper::getBucketInfo(const spi::Bucket& bucket) const
{
    LOG_SPI("getBucketInfo(" << bucket << ")");
    CHECK_ERROR(spi::BucketInfoResult, FAIL_BUCKET_INFO);
    return _spi.getBucketInfo(bucket);
}

spi::Result
PersistenceProviderWrapper::put(const spi::Bucket& bucket, spi::Timestamp timestamp,
                                document::Document::SP doc, spi::Context& context)
{
    LOG_SPI("put(" << bucket << ", " << timestamp << ", " << doc->getId() << ")");
    CHECK_ERROR(spi::Result, FAIL_PUT);
    return _spi.put(bucket, timestamp, std::move(doc), context);
}

spi::RemoveResult
PersistenceProviderWrapper::remove(const spi::Bucket& bucket,
                                   spi::Timestamp timestamp,
                                   const spi::DocumentId& id,
                                   spi::Context& context)
{
    LOG_SPI("remove(" << bucket << ", " << timestamp << ", " << id << ")");
    CHECK_ERROR(spi::RemoveResult, FAIL_REMOVE);
    return _spi.remove(bucket, timestamp, id, context);
}

spi::RemoveResult
PersistenceProviderWrapper::removeIfFound(const spi::Bucket& bucket,
                                          spi::Timestamp timestamp,
                                          const spi::DocumentId& id,
                                          spi::Context& context)
{
    LOG_SPI("removeIfFound(" << bucket << ", " << timestamp << ", " << id << ")");
    CHECK_ERROR(spi::RemoveResult, FAIL_REMOVE_IF_FOUND);
    return _spi.removeIfFound(bucket, timestamp, id, context);
}

spi::UpdateResult
PersistenceProviderWrapper::update(const spi::Bucket& bucket,
                                   spi::Timestamp timestamp,
                                   document::DocumentUpdate::SP upd,
                                   spi::Context& context)
{
    LOG_SPI("update(" << bucket << ", " << timestamp << ", " << upd->getId() << ")");
    CHECK_ERROR(spi::UpdateResult, FAIL_UPDATE);
    return _spi.update(bucket, timestamp, std::move(upd), context);
}

spi::GetResult
PersistenceProviderWrapper::get(const spi::Bucket& bucket,
                                const document::FieldSet& fieldSet,
                                const spi::DocumentId& id,
                                spi::Context& context) const
{
    LOG_SPI("get(" << bucket << ", " << id << ")");
    CHECK_ERROR(spi::GetResult, FAIL_GET);
    return _spi.get(bucket, fieldSet, id, context);
}

spi::CreateIteratorResult
PersistenceProviderWrapper::createIterator(const spi::Bucket &bucket, FieldSetSP fields, const spi::Selection &sel,
                                           spi::IncludedVersions versions,
                                           spi::Context &context)
{
    // TODO: proper printing of FieldSet and Selection

    LOG_SPI("createIterator(" << bucket << ", "
            << includedVersionsToString(versions) << ")");
    CHECK_ERROR(spi::CreateIteratorResult, FAIL_CREATE_ITERATOR);
    return _spi.createIterator(bucket, fields, sel, versions, context);
}

spi::IterateResult
PersistenceProviderWrapper::iterate(spi::IteratorId iterId,
                                    uint64_t maxByteSize,
                                    spi::Context& context) const
{
    LOG_SPI("iterate(" << uint64_t(iterId) << ", " << maxByteSize << ")");
    CHECK_ERROR(spi::IterateResult, FAIL_ITERATE);
    return _spi.iterate(iterId, maxByteSize, context);
}

spi::Result
PersistenceProviderWrapper::destroyIterator(spi::IteratorId iterId,
                                            spi::Context& context)
{
    LOG_SPI("destroyIterator(" << uint64_t(iterId) << ")");
    CHECK_ERROR(spi::Result, FAIL_DESTROY_ITERATOR);
    return _spi.destroyIterator(iterId, context);
}

spi::Result
PersistenceProviderWrapper::deleteBucket(const spi::Bucket& bucket,
                                         spi::Context& context)
{
    LOG_SPI("deleteBucket(" << bucket << ")");
    CHECK_ERROR(spi::Result, FAIL_DELETE_BUCKET);
    return _spi.deleteBucket(bucket, context);
}

spi::Result
PersistenceProviderWrapper::split(const spi::Bucket& source,
                                  const spi::Bucket& target1,
                                  const spi::Bucket& target2,
                                  spi::Context& context)
{
    LOG_SPI("split(" << source << ", " << target1 << ", " << target2 << ")");
    CHECK_ERROR(spi::Result, FAIL_SPLIT);
    return _spi.split(source, target1, target2, context);
}

spi::Result
PersistenceProviderWrapper::join(const spi::Bucket& source1,
                                 const spi::Bucket& source2,
                                 const spi::Bucket& target,
                                 spi::Context& context)
{
    LOG_SPI("join(" << source1 << ", " << source2 << ", " << target << ")");
    CHECK_ERROR(spi::Result, FAIL_JOIN);
    return _spi.join(source1, source2, target, context);
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
PersistenceProviderWrapper::removeEntry(const spi::Bucket& bucket,
                                        spi::Timestamp timestamp,
                                        spi::Context& context)
{
    LOG_SPI("revert(" << bucket << ", " << timestamp << ")");
    CHECK_ERROR(spi::Result, FAIL_REVERT);
    return _spi.removeEntry(bucket, timestamp, context);
}

}
