// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "providershutdownwrapper.h"
#include "persistenceutil.h"
#include <vespa/log/log.h>

LOG_SETUP(".persistence.shutdownwrapper");

namespace storage {

template <typename ResultType>
ResultType
ProviderShutdownWrapper::checkResult(ResultType&& result) const
{
    if (result.getErrorCode() == spi::Result::FATAL_ERROR) {
        vespalib::LockGuard guard(_shutdownLock);
        if (_shutdownTriggered) {
            LOG(debug,
                "Received FATAL_ERROR from persistence provider: %s. "
                "Node has already been instructed to shut down so "
                "not doing anything now.",
                result.getErrorMessage().c_str());
        } else {
            LOG(info,
                "Received FATAL_ERROR from persistence provider, "
                "shutting down node: %s",
                result.getErrorMessage().c_str());
            const_cast<ProviderShutdownWrapper*>(this)->
                _component.requestShutdown(result.getErrorMessage());
            _shutdownTriggered = true;
        }
    }
    return std::move(result);
}

spi::Result
ProviderShutdownWrapper::initialize()
{
    return checkResult(_impl.initialize());
}

spi::PartitionStateListResult
ProviderShutdownWrapper::getPartitionStates() const
{
    return checkResult(_impl.getPartitionStates());
}

spi::BucketIdListResult
ProviderShutdownWrapper::listBuckets(spi::PartitionId partitionId) const
{
    return checkResult(_impl.listBuckets(partitionId));
}

spi::Result
ProviderShutdownWrapper::setClusterState(const spi::ClusterState& state)
{
    return checkResult(_impl.setClusterState(state));
}

spi::Result
ProviderShutdownWrapper::setActiveState(const spi::Bucket& bucket,
                                        spi::BucketInfo::ActiveState newState)
{
    return checkResult(_impl.setActiveState(bucket, newState));
}

spi::BucketInfoResult
ProviderShutdownWrapper::getBucketInfo(const spi::Bucket& bucket) const
{
    return checkResult(_impl.getBucketInfo(bucket));
}

spi::Result
ProviderShutdownWrapper::put(const spi::Bucket& bucket,
                             spi::Timestamp ts,
                             const spi::DocumentSP& doc,
                             spi::Context& context)
{
    return checkResult(_impl.put(bucket, ts, doc, context));
}

spi::RemoveResult
ProviderShutdownWrapper::remove(const spi::Bucket& bucket,
                                spi::Timestamp ts,
                                const document::DocumentId& docId,
                                spi::Context& context)
{
    return checkResult(_impl.remove(bucket, ts, docId, context));
}

spi::RemoveResult
ProviderShutdownWrapper::removeIfFound(const spi::Bucket& bucket,
                                       spi::Timestamp ts,
                                       const document::DocumentId& docId,
                                       spi::Context& context)
{
    return checkResult(_impl.removeIfFound(bucket, ts, docId, context));
}

spi::UpdateResult
ProviderShutdownWrapper::update(const spi::Bucket& bucket,
                                spi::Timestamp ts,
                                const spi::DocumentUpdateSP& docUpdate,
                                spi::Context& context)
{
    return checkResult(_impl.update(bucket, ts, docUpdate, context));
}

spi::GetResult
ProviderShutdownWrapper::get(const spi::Bucket& bucket,
                             const document::FieldSet& fieldSet,
                             const document::DocumentId& docId,
                             spi::Context& context) const
{
    return checkResult(_impl.get(bucket, fieldSet, docId, context));
}

spi::Result
ProviderShutdownWrapper::flush(const spi::Bucket& bucket, spi::Context& context)
{
    return checkResult(_impl.flush(bucket, context));
}

spi::CreateIteratorResult
ProviderShutdownWrapper::createIterator(const spi::Bucket& bucket,
                                        const document::FieldSet& fieldSet,
                                        const spi::Selection& selection,
                                        spi::IncludedVersions versions,
                                        spi::Context& context)
{
    return checkResult(_impl.createIterator(bucket, fieldSet, selection, versions, context));
}

spi::IterateResult
ProviderShutdownWrapper::iterate(spi::IteratorId iteratorId,
                                 uint64_t maxByteSize,
                                 spi::Context& context) const
{
    return checkResult(_impl.iterate(iteratorId, maxByteSize, context));
}

spi::Result
ProviderShutdownWrapper::destroyIterator(spi::IteratorId iteratorId,
                                         spi::Context& context)
{
    return checkResult(_impl.destroyIterator(iteratorId, context));
}

spi::Result
ProviderShutdownWrapper::createBucket(const spi::Bucket& bucket,
                                      spi::Context& context)
{
    return checkResult(_impl.createBucket(bucket, context));
}

spi::Result
ProviderShutdownWrapper::deleteBucket(const spi::Bucket& bucket,
                                      spi::Context& context)
{
    return checkResult(_impl.deleteBucket(bucket, context));
}

spi::BucketIdListResult
ProviderShutdownWrapper::getModifiedBuckets() const
{
    return checkResult(_impl.getModifiedBuckets());
}

spi::Result
ProviderShutdownWrapper::maintain(const spi::Bucket& bucket,
                                  spi::MaintenanceLevel level)
{
    return checkResult(_impl.maintain(bucket, level));
}

spi::Result
ProviderShutdownWrapper::split(const spi::Bucket& source,
                               const spi::Bucket& target1,
                               const spi::Bucket& target2,
                               spi::Context& context)
{
    return checkResult(_impl.split(source, target1, target2, context));
}

spi::Result
ProviderShutdownWrapper::join(const spi::Bucket& source1,
                              const spi::Bucket& source2,
                              const spi::Bucket& target, spi::Context& context)
{
    return checkResult(_impl.join(source1, source2, target, context));
}

spi::Result
ProviderShutdownWrapper::move(const spi::Bucket& source,
                              spi::PartitionId target, spi::Context& context)
{
    return checkResult(_impl.move(source, target, context));
}

spi::Result
ProviderShutdownWrapper::removeEntry(const spi::Bucket& bucket,
                                spi::Timestamp ts, spi::Context& context)
{
    return checkResult(_impl.removeEntry(bucket, ts, context));
}

} // ns storage
