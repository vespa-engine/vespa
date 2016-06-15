// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/memfilepersistence/init/filescanner.h>
#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/memfilepersistence/spi/operationhandler.h>
#include <vespa/memfilepersistence/spi/iteratorhandler.h>
#include <vespa/memfilepersistence/spi/joinoperationhandler.h>
#include <vespa/memfilepersistence/spi/splitoperationhandler.h>
#include <vespa/memfilepersistence/common/types.h>
#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/memfilepersistence/spi/memfilepersistenceprovidermetrics.h>
#include <vespa/memfilepersistence/spi/threadmetricprovider.h>
#include <vespa/storageframework/generic/status/httpurlpath.h>
#include <vespa/memfilepersistence/spi/threadlocals.h>
#include <vespa/config/config.h>

namespace storage {

namespace memfile {

class ThreadContext {
public:
    MemFilePtr _memFile;
    MemFilePersistenceThreadMetrics* _metrics;

    ThreadContext()
        : _metrics(NULL)
    {}
};

class MemFilePersistenceProvider : public spi::AbstractPersistenceProvider,
                                   public framework::Component,
                                   public Types,
                                   public framework::StatusReporter,
                                   public ThreadMetricProvider
{
public:
    typedef std::unique_ptr<MemFilePersistenceProvider> UP;

    MemFilePersistenceProvider(
            framework::ComponentRegister& reg,
            const config::ConfigUri & configUri);

    ~MemFilePersistenceProvider();

    spi::PartitionStateListResult getPartitionStates() const;

    spi::BucketIdListResult listBuckets(spi::PartitionId) const;

    spi::BucketIdListResult getModifiedBuckets() const;

    spi::BucketInfoResult getBucketInfo(const spi::Bucket&) const;

    spi::Result put(const spi::Bucket&, spi::Timestamp,
                    const document::Document::SP&, spi::Context&);

    spi::RemoveResult remove(const spi::Bucket&, spi::Timestamp,
                             const DocumentId&, spi::Context&);

    spi::RemoveResult removeIfFound(const spi::Bucket&, spi::Timestamp,
                                    const DocumentId&, spi::Context&);

    spi::UpdateResult update(const spi::Bucket&, spi::Timestamp,
                             const document::DocumentUpdate::SP&, spi::Context&);

    spi::GetResult get(const spi::Bucket&, const document::FieldSet&,
                       const spi::DocumentId&, spi::Context&) const;

    spi::Result flush(const spi::Bucket&, spi::Context&);

    spi::CreateIteratorResult createIterator(const spi::Bucket&,
                                             const document::FieldSet&,
                                             const spi::Selection&,
                                             spi::IncludedVersions versions,
                                             spi::Context&);

    spi::IterateResult iterate(spi::IteratorId,
                               uint64_t maxByteSize, spi::Context&) const;

    spi::Result destroyIterator(spi::IteratorId, spi::Context&);

    spi::Result deleteBucket(const spi::Bucket&, spi::Context&);

    spi::Result split(const spi::Bucket& source,
                      const spi::Bucket& target1,
                      const spi::Bucket& target2,
                      spi::Context&);

    spi::Result join(const spi::Bucket& source1,
                     const spi::Bucket& source2,
                     const spi::Bucket& target,
                     spi::Context&);

    spi::Result removeEntry(const spi::Bucket&,
                            spi::Timestamp, spi::Context&);

    spi::Result maintain(const spi::Bucket&,
                         spi::MaintenanceLevel level);

    Environment& getEnvironment() {
        return *_env;
    }

    virtual vespalib::string getReportContentType(
            const framework::HttpUrlPath&) const;
    virtual bool reportStatus(std::ostream&,
                              const framework::HttpUrlPath&) const;

    /**
       Used by unit tests.
    */
    void clearActiveMemFile(spi::Context* = 0) const;
    const IteratorHandler& getIteratorHandler() const { return *_iteratorHandler; }

    MemFilePersistenceThreadMetrics& getMetrics() const;

    void setDocumentRepo(const document::DocumentTypeRepo& repo);
    void setConfig(std::unique_ptr<vespa::config::storage::StorMemfilepersistenceConfig> config);
    void setConfig(std::unique_ptr<vespa::config::content::PersistenceConfig> config);
    void setConfig(std::unique_ptr<vespa::config::storage::StorDevicesConfig> config);
private:
    framework::ComponentRegister& _componentRegister;

    config::ConfigUri _configUri;
    vespa::config::storage::StorMemfilepersistenceConfig _config;
    mutable MemFileMapper _memFileMapper;

    const document::DocumentTypeRepo* _repo;
    mutable MemFileCache::UP _cache;
    mutable Environment::UP _env;
    mutable FileScanner::UP _fileScanner;
    mutable OperationHandler::UP _util;
    mutable IteratorHandler::UP _iteratorHandler;
    mutable JoinOperationHandler::UP _joinOperationHandler;
    mutable SplitOperationHandler::UP _splitOperationHandler;
    mutable MemFilePersistenceMetrics _metrics;

    mutable ThreadLocals<ThreadContext> _threadLocals;

    std::pair<spi::Result::ErrorType, vespalib::string>
    getErrorFromException(const std::exception& e);

    MemFilePtr getMemFile(const spi::Bucket& b, bool keepInCache = true) const;
    void setActiveMemFile(MemFilePtr ptr, const char* user) const;
    bool hasCachedMemFile() const;

    template<typename C> C handleException(const std::exception& e,
                                           bool canRepairBucket) const;

    void handleBucketCorruption(const FileSpecification& file) const;

    //void addBucketToNotifySet(const MemFile& file) const;

    MemFilePtr& getThreadLocalMemFile() const;

    friend class MemFileAccessGuard;
};

}

}

