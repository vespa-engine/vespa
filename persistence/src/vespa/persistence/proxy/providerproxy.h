// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/frt/frt.h>
#include <vespa/persistence/spi/persistenceprovider.h>

namespace storage {
namespace spi {

class ProviderProxy : public PersistenceProvider {
    mutable FRT_Supervisor _supervisor;
    FRT_Target *_target;
    const document::DocumentTypeRepo *_repo;

    template <typename ResultType>
    ResultType invokeRpc_Return(FRT_RPCRequest &req,
                                const char *res_spec) const;
    template <typename ResultType>
    ResultType readResult(const FRT_Values &values) const;
    template <typename ResultType>
    ResultType readNoError(const FRT_Values &values) const;

public:
    typedef std::unique_ptr<ProviderProxy> UP;

    ProviderProxy(const vespalib::string &connect_spec,
                  const document::DocumentTypeRepo &repo);
    ~ProviderProxy();

    void setRepo(const document::DocumentTypeRepo &repo) {
        _repo = &repo;
    }

    virtual Result initialize();
    virtual PartitionStateListResult getPartitionStates() const;
    virtual BucketIdListResult listBuckets(PartitionId) const;
    virtual Result setClusterState(const ClusterState&);
    virtual Result setActiveState(const Bucket&, BucketInfo::ActiveState);
    virtual BucketInfoResult getBucketInfo(const Bucket &) const;

    virtual Result put(const Bucket &, Timestamp, const Document::SP&, Context&);
    virtual RemoveResult remove(const Bucket &, Timestamp, const DocumentId &,
                                Context&);
    virtual RemoveResult removeIfFound(const Bucket &, Timestamp,
                                       const DocumentId &, Context&);
    virtual UpdateResult update(const Bucket &, Timestamp,
                                const DocumentUpdate::SP&, Context&);

    virtual Result flush(const Bucket &, Context&);

    virtual GetResult get(const Bucket &,
                          const document::FieldSet&,
                          const DocumentId &,
                          Context&) const;

    virtual CreateIteratorResult createIterator(const Bucket &,
                                                const document::FieldSet&,
                                                const Selection&,
                                                IncludedVersions versions,
                                                Context&);

    virtual IterateResult iterate(IteratorId, uint64_t max_byte_size,
                                  Context&) const;
    virtual Result destroyIterator(IteratorId, Context&);

    virtual Result createBucket(const Bucket &, Context&);
    virtual Result deleteBucket(const Bucket &, Context&);
    virtual BucketIdListResult getModifiedBuckets() const;
    virtual Result split(const Bucket &source,
                         const Bucket &target1,
                         const Bucket &target2,
                         Context&);

    virtual Result join(const Bucket &source1,
                        const Bucket &source2,
                        const Bucket &target,
                        Context&);

    virtual Result move(const Bucket &source,
                        PartitionId partition,
                        Context&);

    virtual Result maintain(const Bucket &, MaintenanceLevel);
    virtual Result removeEntry(const Bucket &, Timestamp, Context&);
};

}  // namespace spi
}  // namespace storage

