// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "operationhandler.h"
#include "iteratorhandler.h"
#include "joinoperationhandler.h"
#include "splitoperationhandler.h"
#include "memfilepersistenceprovidermetrics.h"
#include "threadmetricprovider.h"
#include "threadlocals.h"
#include <vespa/memfilepersistence/common/types.h>
#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/memfilepersistence/init/filescanner.h>
#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/storageframework/generic/status/httpurlpath.h>

#include <vespa/config/config.h>

namespace storage::memfile {

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

    MemFilePersistenceProvider(framework::ComponentRegister& reg, const config::ConfigUri & configUri);
    ~MemFilePersistenceProvider();

    spi::PartitionStateListResult getPartitionStates() const override;
    spi::BucketIdListResult listBuckets(spi::PartitionId) const override;
    spi::BucketIdListResult getModifiedBuckets() const override;
    spi::BucketInfoResult getBucketInfo(const spi::Bucket&) const override;
    spi::Result put(const spi::Bucket&, spi::Timestamp,
                    const spi::DocumentSP&, spi::Context&) override;

    spi::RemoveResult remove(const spi::Bucket&, spi::Timestamp,
                             const DocumentId&, spi::Context&) override;

    spi::RemoveResult removeIfFound(const spi::Bucket&, spi::Timestamp,
                                    const DocumentId&, spi::Context&) override;

    spi::UpdateResult update(const spi::Bucket&, spi::Timestamp,
                             const spi::DocumentUpdateSP&, spi::Context&) override;

    spi::GetResult get(const spi::Bucket&, const document::FieldSet&,
                       const spi::DocumentId&, spi::Context&) const override;

    spi::Result flush(const spi::Bucket&, spi::Context&) override;

    spi::CreateIteratorResult createIterator(const spi::Bucket&, const document::FieldSet&, const spi::Selection&,
                                             spi::IncludedVersions versions, spi::Context&) override;

    spi::IterateResult iterate(spi::IteratorId, uint64_t maxByteSize, spi::Context&) const override;
    spi::Result destroyIterator(spi::IteratorId, spi::Context&) override;
    spi::Result deleteBucket(const spi::Bucket&, spi::Context&) override;
    spi::Result split(const spi::Bucket& source, const spi::Bucket& target1,
                      const spi::Bucket& target2, spi::Context&) override;

    spi::Result join(const spi::Bucket& source1, const spi::Bucket& source2,
                     const spi::Bucket& target, spi::Context&) override;

    spi::Result removeEntry(const spi::Bucket&, spi::Timestamp, spi::Context&) override;
    spi::Result maintain(const spi::Bucket&, spi::MaintenanceLevel level) override;

    Environment& getEnvironment() { return *_env; }

    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    /**
       Used by unit tests.
    */
    void clearActiveMemFile(spi::Context* = 0) const;
    const IteratorHandler& getIteratorHandler() const { return *_iteratorHandler; }

    MemFilePersistenceThreadMetrics& getMetrics() const override;

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

    std::pair<spi::Result::ErrorType, vespalib::string> getErrorFromException(const std::exception& e);

    MemFilePtr getMemFile(const spi::Bucket& b, bool keepInCache = true) const;
    void setActiveMemFile(MemFilePtr ptr, const char* user) const;
    bool hasCachedMemFile() const;

    template<typename C> C handleException(const std::exception& e, bool canRepairBucket) const;

    void handleBucketCorruption(const FileSpecification& file) const;

    //void addBucketToNotifySet(const MemFile& file) const;

    MemFilePtr& getThreadLocalMemFile() const;

    friend class MemFileAccessGuard;
};

}

