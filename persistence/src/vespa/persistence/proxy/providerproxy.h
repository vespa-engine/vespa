// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>

class FRT_Target;
class FRT_Supervisor;
class FRT_RPCRequest;
class FRT_Values;

namespace document {
    class DocumentTypeRepo;
}

namespace storage {
namespace spi {

class ProviderProxy : public PersistenceProvider {
    std::unique_ptr<FRT_Supervisor> _supervisor;
    FRT_Target *_target;
    const document::DocumentTypeRepo *_repo;

    template <typename ResultType>
    ResultType invokeRpc_Return(FRT_RPCRequest &req, const char *res_spec) const;
    template <typename ResultType>
    ResultType readResult(const FRT_Values &values) const;
    template <typename ResultType>
    ResultType readNoError(const FRT_Values &values) const;

public:
    typedef std::unique_ptr<ProviderProxy> UP;

    ProviderProxy(const vespalib::string &connect_spec, const document::DocumentTypeRepo &repo);
    ~ProviderProxy();

    void setRepo(const document::DocumentTypeRepo &repo) {
        _repo = &repo;
    }

    Result initialize() override;
    PartitionStateListResult getPartitionStates() const override;
    BucketIdListResult listBuckets(PartitionId) const override;
    Result setClusterState(const ClusterState&) override;
    Result setActiveState(const Bucket&, BucketInfo::ActiveState) override;
    BucketInfoResult getBucketInfo(const Bucket &) const override;

    Result put(const Bucket &, Timestamp, const DocumentSP&, Context&) override;
    RemoveResult remove(const Bucket &, Timestamp, const DocumentId &, Context&) override;
    RemoveResult removeIfFound(const Bucket &, Timestamp, const DocumentId &, Context&) override;
    UpdateResult update(const Bucket &, Timestamp, const DocumentUpdateSP&, Context&) override;

    Result flush(const Bucket &, Context&) override;

    GetResult get(const Bucket &, const document::FieldSet&, const DocumentId &, Context&) const override;

    CreateIteratorResult createIterator(const Bucket &, const document::FieldSet&, const Selection&,
                                                IncludedVersions versions, Context&) override;

    IterateResult iterate(IteratorId, uint64_t max_byte_size, Context&) const override;
    Result destroyIterator(IteratorId, Context&) override;

    Result createBucket(const Bucket &, Context&) override;
    Result deleteBucket(const Bucket &, Context&) override;
    BucketIdListResult getModifiedBuckets() const override;
    Result split(const Bucket &source, const Bucket &target1, const Bucket &target2, Context&) override;
    Result join(const Bucket &source1, const Bucket &source2, const Bucket &target, Context&) override;
    Result move(const Bucket &source, PartitionId partition, Context&) override;

    Result maintain(const Bucket &, MaintenanceLevel) override;
    Result removeEntry(const Bucket &, Timestamp, Context&) override;
};

}  // namespace spi
}  // namespace storage

