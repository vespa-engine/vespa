// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::PersistenceProviderWrapper
 *
 * \brief Test utility class for intercepting all operations upon a
 * persistence layer, injecting errors and performing logging.
 *
 * The PersistenceProviderWrapper class implements the basic SPI by
 * logging all operations and then delegating handling the operation
 * to the SPI instance given during construction. If an error result
 * is specified and the operation invoked is tagged that it should be
 * failed via setFailureMask(), the operation on the wrapped SPI will
 * not be executed, but the given error result will be immediately
 * returned instead (wrapped in the proper return type).
 */
#pragma once


#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <mutex>

namespace storage {

class PersistenceProviderWrapper : public spi::AbstractPersistenceProvider
{
public:
    enum OPERATION_FAILURE_FLAGS
    {
        FAIL_LIST_BUCKETS     = 1 << 0,
        FAIL_BUCKET_INFO      = 1 << 1,
        FAIL_GET              = 1 << 2,
        FAIL_PUT              = 1 << 3,
        FAIL_REMOVE           = 1 << 4,
        FAIL_REMOVE_IF_FOUND  = 1 << 5,
        FAIL_REPLACE_WITH_REMOVE = 1 << 6,
        FAIL_UPDATE           = 1 << 7,
        FAIL_REVERT           = 1 << 8,
        FAIL_CREATE_ITERATOR  = 1 << 10,
        FAIL_ITERATE          = 1 << 11,
        FAIL_DESTROY_ITERATOR = 1 << 12,
        FAIL_DELETE_BUCKET    = 1 << 13,
        FAIL_SPLIT            = 1 << 14,
        FAIL_JOIN             = 1 << 15,
        FAIL_CREATE_BUCKET    = 1 << 16,
        FAIL_BUCKET_PERSISTENCE = FAIL_PUT|FAIL_REMOVE|FAIL_UPDATE|FAIL_REVERT,
        FAIL_ALL_OPERATIONS   = 0xffff,
        // TODO: add more as needed
    };
private:
    spi::PersistenceProvider& _spi;
    spi::Result _result;
    mutable std::mutex  _lock;
    mutable std::vector<std::string> _log;
    uint32_t _failureMask;
    using Guard = std::lock_guard<std::mutex>;
public:
    PersistenceProviderWrapper(spi::PersistenceProvider& spi);
    ~PersistenceProviderWrapper() override;

    /**
     * Explicitly set result to anything != NONE to have all operations
     * return the given error without the wrapped SPI ever being invoked.
     */
    void setResult(const spi::Result& result) {
        Guard guard(_lock);
        _result = result;
    }
    spi::Result getResult() const {
        Guard guard(_lock);
        return _result;
    }
    /**
     * Set a mask for operations to fail with _result
     */
    void setFailureMask(uint32_t mask) { _failureMask = mask; }
    uint32_t getFailureMask() const { return _failureMask; }

    /**
     * Get a string representation of all the operations performed on the
     * SPI with a newline separating each operation.
     */
    std::string toString() const;
    /**
     * Clear log of all operations performed.
     */
    void clearOperationLog() {
        Guard guard(_lock);
        _log.clear();
    }

    spi::Result createBucket(const spi::Bucket&, spi::Context&) override;
    spi::BucketIdListResult listBuckets(BucketSpace bucketSpace) const override;
    spi::BucketInfoResult getBucketInfo(const spi::Bucket&) const override;
    spi::Result put(const spi::Bucket&, spi::Timestamp, spi::DocumentSP, spi::Context&) override;
    spi::RemoveResult remove(const spi::Bucket&, spi::Timestamp, const spi::DocumentId&, spi::Context&) override;
    spi::RemoveResult removeIfFound(const spi::Bucket&, spi::Timestamp, const spi::DocumentId&, spi::Context&) override;
    spi::UpdateResult update(const spi::Bucket&, spi::Timestamp, spi::DocumentUpdateSP, spi::Context&) override;
    spi::GetResult get(const spi::Bucket&, const document::FieldSet&, const spi::DocumentId&, spi::Context&) const override;

    spi::CreateIteratorResult
    createIterator(const spi::Bucket &bucket, FieldSetSP, const spi::Selection &, spi::IncludedVersions versions,
                   spi::Context &context) override;

    spi::IterateResult iterate(spi::IteratorId, uint64_t maxByteSize, spi::Context&) const override;
    spi::Result destroyIterator(spi::IteratorId, spi::Context&) override;
    spi::Result deleteBucket(const spi::Bucket&, spi::Context&) override;
    spi::Result split(const spi::Bucket& source, const spi::Bucket& target1,
                      const spi::Bucket& target2, spi::Context&) override;
    spi::Result join(const spi::Bucket& source1, const spi::Bucket& source2,
                     const spi::Bucket& target, spi::Context&) override;
    spi::Result removeEntry(const spi::Bucket&, spi::Timestamp, spi::Context&) override;
    std::unique_ptr<vespalib::IDestructorCallback> register_resource_usage_listener(spi::IResourceUsageListener& listener) override;
    std::unique_ptr<vespalib::IDestructorCallback> register_executor(std::shared_ptr<spi::BucketExecutor>) override;
};

} // storage
