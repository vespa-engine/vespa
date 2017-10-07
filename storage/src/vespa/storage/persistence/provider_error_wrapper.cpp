// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "provider_error_wrapper.h"
#include "persistenceutil.h"

namespace storage {

template <typename ResultType>
ResultType
ProviderErrorWrapper::checkResult(ResultType&& result) const
{
    if (result.getErrorCode() == spi::Result::FATAL_ERROR) {
        trigger_shutdown_listeners(result.getErrorMessage());
    } else if (result.getErrorCode() == spi::Result::RESOURCE_EXHAUSTED) {
        trigger_resource_exhaustion_listeners(result.getErrorMessage());
    }
    return std::forward<ResultType>(result);
}

void ProviderErrorWrapper::trigger_shutdown_listeners(vespalib::stringref reason) const {
    std::lock_guard<std::mutex> guard(_mutex);
    for (auto& listener : _listeners) {
        listener->on_fatal_error(reason);
    }
}

void ProviderErrorWrapper::trigger_resource_exhaustion_listeners(vespalib::stringref reason) const {
    std::lock_guard<std::mutex> guard(_mutex);
    for (auto& listener : _listeners) {
        listener->on_resource_exhaustion_error(reason);
    }
}

void ProviderErrorWrapper::register_error_listener(std::shared_ptr<ProviderErrorListener> listener) {
    std::lock_guard<std::mutex> guard(_mutex);
    _listeners.emplace_back(std::move(listener));
}

spi::Result
ProviderErrorWrapper::initialize()
{
    return checkResult(_impl.initialize());
}

spi::PartitionStateListResult
ProviderErrorWrapper::getPartitionStates() const
{
    return checkResult(_impl.getPartitionStates());
}

spi::BucketIdListResult
ProviderErrorWrapper::listBuckets(BucketSpace bucketSpace, spi::PartitionId partitionId) const
{
    return checkResult(_impl.listBuckets(bucketSpace, partitionId));
}

spi::Result
ProviderErrorWrapper::setClusterState(const spi::ClusterState& state)
{
    return checkResult(_impl.setClusterState(state));
}

spi::Result
ProviderErrorWrapper::setActiveState(const spi::Bucket& bucket,
                                        spi::BucketInfo::ActiveState newState)
{
    return checkResult(_impl.setActiveState(bucket, newState));
}

spi::BucketInfoResult
ProviderErrorWrapper::getBucketInfo(const spi::Bucket& bucket) const
{
    return checkResult(_impl.getBucketInfo(bucket));
}

spi::Result
ProviderErrorWrapper::put(const spi::Bucket& bucket,
                             spi::Timestamp ts,
                             const spi::DocumentSP& doc,
                             spi::Context& context)
{
    return checkResult(_impl.put(bucket, ts, doc, context));
}

spi::RemoveResult
ProviderErrorWrapper::remove(const spi::Bucket& bucket,
                                spi::Timestamp ts,
                                const document::DocumentId& docId,
                                spi::Context& context)
{
    return checkResult(_impl.remove(bucket, ts, docId, context));
}

spi::RemoveResult
ProviderErrorWrapper::removeIfFound(const spi::Bucket& bucket,
                                       spi::Timestamp ts,
                                       const document::DocumentId& docId,
                                       spi::Context& context)
{
    return checkResult(_impl.removeIfFound(bucket, ts, docId, context));
}

spi::UpdateResult
ProviderErrorWrapper::update(const spi::Bucket& bucket,
                                spi::Timestamp ts,
                                const spi::DocumentUpdateSP& docUpdate,
                                spi::Context& context)
{
    return checkResult(_impl.update(bucket, ts, docUpdate, context));
}

spi::GetResult
ProviderErrorWrapper::get(const spi::Bucket& bucket,
                             const document::FieldSet& fieldSet,
                             const document::DocumentId& docId,
                             spi::Context& context) const
{
    return checkResult(_impl.get(bucket, fieldSet, docId, context));
}

spi::Result
ProviderErrorWrapper::flush(const spi::Bucket& bucket, spi::Context& context)
{
    return checkResult(_impl.flush(bucket, context));
}

spi::CreateIteratorResult
ProviderErrorWrapper::createIterator(const spi::Bucket& bucket,
                                        const document::FieldSet& fieldSet,
                                        const spi::Selection& selection,
                                        spi::IncludedVersions versions,
                                        spi::Context& context)
{
    return checkResult(_impl.createIterator(bucket, fieldSet, selection, versions, context));
}

spi::IterateResult
ProviderErrorWrapper::iterate(spi::IteratorId iteratorId,
                                 uint64_t maxByteSize,
                                 spi::Context& context) const
{
    return checkResult(_impl.iterate(iteratorId, maxByteSize, context));
}

spi::Result
ProviderErrorWrapper::destroyIterator(spi::IteratorId iteratorId,
                                         spi::Context& context)
{
    return checkResult(_impl.destroyIterator(iteratorId, context));
}

spi::Result
ProviderErrorWrapper::createBucket(const spi::Bucket& bucket,
                                      spi::Context& context)
{
    return checkResult(_impl.createBucket(bucket, context));
}

spi::Result
ProviderErrorWrapper::deleteBucket(const spi::Bucket& bucket,
                                      spi::Context& context)
{
    return checkResult(_impl.deleteBucket(bucket, context));
}

spi::BucketIdListResult
ProviderErrorWrapper::getModifiedBuckets(BucketSpace bucketSpace) const
{
    return checkResult(_impl.getModifiedBuckets(bucketSpace));
}

spi::Result
ProviderErrorWrapper::maintain(const spi::Bucket& bucket,
                                  spi::MaintenanceLevel level)
{
    return checkResult(_impl.maintain(bucket, level));
}

spi::Result
ProviderErrorWrapper::split(const spi::Bucket& source,
                               const spi::Bucket& target1,
                               const spi::Bucket& target2,
                               spi::Context& context)
{
    return checkResult(_impl.split(source, target1, target2, context));
}

spi::Result
ProviderErrorWrapper::join(const spi::Bucket& source1,
                              const spi::Bucket& source2,
                              const spi::Bucket& target, spi::Context& context)
{
    return checkResult(_impl.join(source1, source2, target, context));
}

spi::Result
ProviderErrorWrapper::move(const spi::Bucket& source,
                              spi::PartitionId target, spi::Context& context)
{
    return checkResult(_impl.move(source, target, context));
}

spi::Result
ProviderErrorWrapper::removeEntry(const spi::Bucket& bucket,
                                spi::Timestamp ts, spi::Context& context)
{
    return checkResult(_impl.removeEntry(bucket, ts, context));
}

} // ns storage
