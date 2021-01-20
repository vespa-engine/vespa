// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::ProviderErrorWrapper
 *
 * \brief Utility class which forwards all calls to the real persistence
 * provider implementation, transparently checking the result of each
 * operation to see if the result is FATAL_ERROR or RESOURCE_EXHAUSTED.
 *
 * If FATAL_ERROR or RESOURCE_EXHAUSTED is observed, the wrapper will invoke any
 * and all resource exhaustion listeners synchronously, before returning the response
 * to the caller as usual.
 */
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>
#include <mutex>

namespace storage {

class ServiceLayerComponent;

class ProviderErrorListener {
public:
    virtual ~ProviderErrorListener() = default;
    virtual void on_fatal_error(vespalib::stringref message) {
        (void)message;
    }
    virtual void on_resource_exhaustion_error(vespalib::stringref message) {
        (void)message;
    }
};

class ProviderErrorWrapper : public spi::PersistenceProvider, public spi::ResultHandler {
public:
    explicit ProviderErrorWrapper(spi::PersistenceProvider& impl)
        : _impl(impl),
          _mutex()
    {
    }

    spi::Result initialize() override;
    spi::BucketIdListResult listBuckets(BucketSpace bucketSpace) const override;
    spi::Result setClusterState(BucketSpace bucketSpace, const spi::ClusterState&)  override;
    spi::Result setActiveState(const spi::Bucket& bucket, spi::BucketInfo::ActiveState newState) override;
    spi::BucketInfoResult getBucketInfo(const spi::Bucket&) const override;
    spi::Result put(const spi::Bucket&, spi::Timestamp, spi::DocumentSP, spi::Context&) override;
    spi::RemoveResult remove(const spi::Bucket&, spi::Timestamp, const document::DocumentId&, spi::Context&) override;
    spi::RemoveResult removeIfFound(const spi::Bucket&, spi::Timestamp, const document::DocumentId&, spi::Context&) override;
    spi::UpdateResult update(const spi::Bucket&, spi::Timestamp, spi::DocumentUpdateSP, spi::Context&) override;
    spi::GetResult get(const spi::Bucket&, const document::FieldSet&, const document::DocumentId&, spi::Context&) const override;
    spi::CreateIteratorResult
    createIterator(const spi::Bucket &bucket, FieldSetSP, const spi::Selection &, spi::IncludedVersions versions,
                   spi::Context &context) override;
    spi::IterateResult iterate(spi::IteratorId, uint64_t maxByteSize, spi::Context&) const override;
    spi::Result destroyIterator(spi::IteratorId, spi::Context&) override;
    spi::Result createBucket(const spi::Bucket&, spi::Context&) override;
    spi::Result deleteBucket(const spi::Bucket&, spi::Context&) override;
    spi::BucketIdListResult getModifiedBuckets(BucketSpace bucketSpace) const override;
    spi::Result split(const spi::Bucket& source, const spi::Bucket& target1, const spi::Bucket& target2, spi::Context&) override;
    spi::Result join(const spi::Bucket& source1, const spi::Bucket& source2, const spi::Bucket& target, spi::Context&) override;
    std::unique_ptr<vespalib::IDestructorCallback> register_resource_usage_listener(spi::IResourceUsageListener& listener) override;
    spi::Result removeEntry(const spi::Bucket&, spi::Timestamp, spi::Context&) override;

    void register_error_listener(std::shared_ptr<ProviderErrorListener> listener);

    void putAsync(const spi::Bucket &, spi::Timestamp, spi::DocumentSP, spi::Context &, spi::OperationComplete::UP) override;
    void removeAsync(const spi::Bucket&, spi::Timestamp, const document::DocumentId&, spi::Context&, spi::OperationComplete::UP) override;
    void removeIfFoundAsync(const spi::Bucket&, spi::Timestamp, const document::DocumentId&, spi::Context&, spi::OperationComplete::UP) override;
    void updateAsync(const spi::Bucket &, spi::Timestamp, spi::DocumentUpdateSP, spi::Context &, spi::OperationComplete::UP) override;
    std::unique_ptr<vespalib::IDestructorCallback> register_executor(std::shared_ptr<spi::BucketExecutor> executor) override;
private:
    template <typename ResultType>
    ResultType checkResult(ResultType&& result) const;
    void handle(const spi::Result &) const override;

    void trigger_shutdown_listeners(vespalib::stringref reason) const;
    void trigger_resource_exhaustion_listeners(vespalib::stringref reason) const;

    spi::PersistenceProvider& _impl;
    std::vector<std::shared_ptr<ProviderErrorListener>> _listeners;
    mutable std::mutex _mutex;
};

} // storage

