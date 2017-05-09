// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::ProviderShutdownWrapper
 *
 * \brief Utility class which forwards all calls to the real persistence
 * provider implementation, transparently checking the result of each
 * operation to see if the result is FATAL_ERROR. If so, it initiates a
 * shutdown of the process (but still returns the response up to the caller
 * as if it were just a non-wrapped call).
 *
 */
#pragma once

#include <vector>
#include <string>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/vespalib/util/sync.h>

namespace storage {

class ServiceLayerComponent;

class ProviderShutdownWrapper : public spi::PersistenceProvider
{
public:
    ProviderShutdownWrapper(spi::PersistenceProvider& impl,
                            ServiceLayerComponent& component)
        : _impl(impl),
          _component(component),
          _shutdownLock(),
          _shutdownTriggered(false)
    {
    }

    spi::Result initialize() override;
    spi::PartitionStateListResult getPartitionStates() const override;
    spi::BucketIdListResult listBuckets(spi::PartitionId) const override;
    spi::Result setClusterState(const spi::ClusterState&)  override;
    spi::Result setActiveState(const spi::Bucket& bucket, spi::BucketInfo::ActiveState newState) override;
    spi::BucketInfoResult getBucketInfo(const spi::Bucket&) const override;
    spi::Result put(const spi::Bucket&, spi::Timestamp, const spi::DocumentSP&, spi::Context&) override;
    spi::RemoveResult remove(const spi::Bucket&, spi::Timestamp, const document::DocumentId&, spi::Context&) override;
    spi::RemoveResult removeIfFound(const spi::Bucket&, spi::Timestamp, const document::DocumentId&, spi::Context&) override;
    spi::UpdateResult update(const spi::Bucket&, spi::Timestamp, const spi::DocumentUpdateSP&, spi::Context&) override;
    spi::GetResult get(const spi::Bucket&, const document::FieldSet&, const document::DocumentId&, spi::Context&) const override;
    spi::Result flush(const spi::Bucket&, spi::Context&) override;
    spi::CreateIteratorResult createIterator(const spi::Bucket&, const document::FieldSet&, const spi::Selection&,
                                             spi::IncludedVersions versions, spi::Context&) override;
    spi::IterateResult iterate(spi::IteratorId, uint64_t maxByteSize, spi::Context&) const override;
    spi::Result destroyIterator(spi::IteratorId, spi::Context&) override;
    spi::Result createBucket(const spi::Bucket&, spi::Context&) override;
    spi::Result deleteBucket(const spi::Bucket&, spi::Context&) override;
    spi::BucketIdListResult getModifiedBuckets() const override;
    spi::Result maintain(const spi::Bucket& bucket, spi::MaintenanceLevel level) override;
    spi::Result split(const spi::Bucket& source, const spi::Bucket& target1, const spi::Bucket& target2, spi::Context&) override;
    spi::Result join(const spi::Bucket& source1, const spi::Bucket& source2, const spi::Bucket& target, spi::Context&) override;
    spi::Result move(const spi::Bucket& source, spi::PartitionId target, spi::Context&) override;
    spi::Result removeEntry(const spi::Bucket&, spi::Timestamp, spi::Context&) override;

    spi::PersistenceProvider& getProviderImplementation() {
        return _impl;
    }
    const spi::PersistenceProvider& getProviderImplementation() const {
        return _impl;
    }
private:
    /**
     * Check whether result has a FATAL_ERROR return code and invoke
     * requestShutdown with its error string if so. Will const_cast
     * internally since it calls non-const on _component.
     */
    template <typename ResultType>
    ResultType checkResult(ResultType&& result) const;

    spi::PersistenceProvider& _impl;
    ServiceLayerComponent& _component;
    vespalib::Lock _shutdownLock;
    mutable bool _shutdownTriggered;
};

} // storage

