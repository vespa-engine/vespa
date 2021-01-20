// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "provider_error_wrapper.h"
#include "persistenceutil.h"
#include <vespa/vespalib/util/idestructorcallback.h>

namespace storage {

template <typename ResultType>
ResultType
ProviderErrorWrapper::checkResult(ResultType&& result) const
{
    handle(result);
    return std::forward<ResultType>(result);
}

void
ProviderErrorWrapper::handle(const spi::Result & result) const {
    if (result.getErrorCode() == spi::Result::ErrorType::FATAL_ERROR) {
        trigger_shutdown_listeners(result.getErrorMessage());
    } else if (result.getErrorCode() == spi::Result::ErrorType::RESOURCE_EXHAUSTED) {
        trigger_resource_exhaustion_listeners(result.getErrorMessage());
    }
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

spi::BucketIdListResult
ProviderErrorWrapper::listBuckets(BucketSpace bucketSpace) const
{
    return checkResult(_impl.listBuckets(bucketSpace));
}

spi::Result
ProviderErrorWrapper::setClusterState(BucketSpace bucketSpace, const spi::ClusterState& state)
{
    return checkResult(_impl.setClusterState(bucketSpace, state));
}

spi::Result
ProviderErrorWrapper::setActiveState(const spi::Bucket& bucket, spi::BucketInfo::ActiveState newState)
{
    return checkResult(_impl.setActiveState(bucket, newState));
}

spi::BucketInfoResult
ProviderErrorWrapper::getBucketInfo(const spi::Bucket& bucket) const
{
    return checkResult(_impl.getBucketInfo(bucket));
}

spi::Result
ProviderErrorWrapper::put(const spi::Bucket& bucket, spi::Timestamp ts, spi::DocumentSP doc, spi::Context& context)
{
    return checkResult(_impl.put(bucket, ts, std::move(doc), context));
}

spi::RemoveResult
ProviderErrorWrapper::remove(const spi::Bucket& bucket, spi::Timestamp ts, const document::DocumentId& docId, spi::Context& context)
{
    return checkResult(_impl.remove(bucket, ts, docId, context));
}

spi::RemoveResult
ProviderErrorWrapper::removeIfFound(const spi::Bucket& bucket, spi::Timestamp ts,
                                    const document::DocumentId& docId, spi::Context& context)
{
    return checkResult(_impl.removeIfFound(bucket, ts, docId, context));
}

spi::UpdateResult
ProviderErrorWrapper::update(const spi::Bucket& bucket, spi::Timestamp ts,
                             spi::DocumentUpdateSP docUpdate, spi::Context& context)
{
    return checkResult(_impl.update(bucket, ts, std::move(docUpdate), context));
}

spi::GetResult
ProviderErrorWrapper::get(const spi::Bucket& bucket, const document::FieldSet& fieldSet,
                          const document::DocumentId& docId, spi::Context& context) const
{
    return checkResult(_impl.get(bucket, fieldSet, docId, context));
}

spi::CreateIteratorResult
ProviderErrorWrapper::createIterator(const spi::Bucket &bucket, FieldSetSP fieldSet, const spi::Selection &selection,
                                     spi::IncludedVersions versions, spi::Context &context)
{
    return checkResult(_impl.createIterator(bucket, fieldSet, selection, versions, context));
}

spi::IterateResult
ProviderErrorWrapper::iterate(spi::IteratorId iteratorId, uint64_t maxByteSize, spi::Context& context) const
{
    return checkResult(_impl.iterate(iteratorId, maxByteSize, context));
}

spi::Result
ProviderErrorWrapper::destroyIterator(spi::IteratorId iteratorId, spi::Context& context)
{
    return checkResult(_impl.destroyIterator(iteratorId, context));
}

spi::Result
ProviderErrorWrapper::createBucket(const spi::Bucket& bucket, spi::Context& context)
{
    return checkResult(_impl.createBucket(bucket, context));
}

spi::Result
ProviderErrorWrapper::deleteBucket(const spi::Bucket& bucket, spi::Context& context)
{
    return checkResult(_impl.deleteBucket(bucket, context));
}

spi::BucketIdListResult
ProviderErrorWrapper::getModifiedBuckets(BucketSpace bucketSpace) const
{
    return checkResult(_impl.getModifiedBuckets(bucketSpace));
}

spi::Result
ProviderErrorWrapper::split(const spi::Bucket& source, const spi::Bucket& target1,
                            const spi::Bucket& target2, spi::Context& context)
{
    return checkResult(_impl.split(source, target1, target2, context));
}

spi::Result
ProviderErrorWrapper::join(const spi::Bucket& source1, const spi::Bucket& source2,
                           const spi::Bucket& target, spi::Context& context)
{
    return checkResult(_impl.join(source1, source2, target, context));
}

std::unique_ptr<vespalib::IDestructorCallback>
ProviderErrorWrapper::register_resource_usage_listener(spi::IResourceUsageListener& listener)
{
    return _impl.register_resource_usage_listener(listener);
}

spi::Result
ProviderErrorWrapper::removeEntry(const spi::Bucket& bucket, spi::Timestamp ts, spi::Context& context)
{
    return checkResult(_impl.removeEntry(bucket, ts, context));
}

void
ProviderErrorWrapper::putAsync(const spi::Bucket &bucket, spi::Timestamp ts, spi::DocumentSP doc,
                               spi::Context &context, spi::OperationComplete::UP onComplete)
{
    onComplete->addResultHandler(this);
    _impl.putAsync(bucket, ts, std::move(doc), context, std::move(onComplete));
}

void
ProviderErrorWrapper::removeAsync(const spi::Bucket &bucket, spi::Timestamp ts, const document::DocumentId &docId,
                                  spi::Context & context, spi::OperationComplete::UP onComplete)
{
    onComplete->addResultHandler(this);
    _impl.removeAsync(bucket, ts, docId, context, std::move(onComplete));
}

void
ProviderErrorWrapper::removeIfFoundAsync(const spi::Bucket &bucket, spi::Timestamp ts, const document::DocumentId &docId,
                                         spi::Context & context, spi::OperationComplete::UP onComplete)
{
    onComplete->addResultHandler(this);
    _impl.removeIfFoundAsync(bucket, ts, docId, context, std::move(onComplete));
}

void
ProviderErrorWrapper::updateAsync(const spi::Bucket &bucket, spi::Timestamp ts, spi::DocumentUpdateSP upd,
                                  spi::Context &context, spi::OperationComplete::UP onComplete)
{
    onComplete->addResultHandler(this);
    _impl.updateAsync(bucket, ts, std::move(upd), context, std::move(onComplete));
}

std::unique_ptr<vespalib::IDestructorCallback>
ProviderErrorWrapper::register_executor(std::shared_ptr<spi::BucketExecutor> executor)
{
    return _impl.register_executor(std::move(executor));
}

} // ns storage
