// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "memfilepersistenceprovider.h"
#include <vespa/memfilepersistence/common/exceptions.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/persistence/spi/fixed_bucket_spaces.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".memfilepersistenceprovider");

#define TRACE(context, level, func, message) \
{ \
    if ((context).getTrace().shouldTrace(level)) { \
        vespalib::string messageToTrace( \
                vespalib::make_string("MemFilePP.%s: %s", func, message)); \
        (context).getTrace().trace(level, messageToTrace); \
    } \
}
#define TRACEGENERIC(context, type) \
if ((context).getTrace().shouldTrace(9)) { \
    vespalib::string messageToTrace( \
            vespalib::make_string("MemFilePP.%s: Load type %s, priority %u.", \
                type, (context).getLoadType().toString().c_str(), \
                (uint32_t) (context).getPriority())); \
    (context).getTrace().trace(9, messageToTrace); \
}

namespace storage::memfile {

namespace {

Device::State
mapIoExceptionToDeviceState(MemFileIoException::Type type)
{
    using vespalib::IoException;
    switch (type) {
        case IoException::ILLEGAL_PATH:
            return Device::PATH_FAILURE;
        case IoException::NO_PERMISSION:
            return Device::NO_PERMISSION;
        case IoException::DISK_PROBLEM:
            return Device::IO_FAILURE;
        case IoException::TOO_MANY_OPEN_FILES:
            return Device::TOO_MANY_OPEN_FILES;
        default:
            return Device::OK;
    }
}

} // end of anonymous namespace

MemFilePtr&
MemFilePersistenceProvider::getThreadLocalMemFile() const
{
    return _threadLocals.get()._memFile;
}

MemFilePersistenceThreadMetrics&
MemFilePersistenceProvider::getMetrics() const
{
    ThreadContext& context = _threadLocals.get();
    if (context._metrics == NULL) {
        context._metrics = _metrics.addThreadMetrics();
    }

    return *context._metrics;
}

bool
MemFilePersistenceProvider::hasCachedMemFile() const
{
    return _threadLocals.get()._memFile.get();
}

MemFilePtr
MemFilePersistenceProvider::getMemFile(const spi::Bucket& b,
                                       bool keepInCache) const
{
    assert(b.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    MemFilePtr& ptr = getThreadLocalMemFile();

    if (ptr.get()) {
        assert(ptr->getFile().getBucketId() == b);

        MemFilePtr retVal = ptr;
        ptr = MemFilePtr();
        return retVal;
    }

    return _env->_cache.get(b.getBucketId(),
                            *_env,
                            _env->getDirectory(b.getPartition()),
                            keepInCache);
}

void
MemFilePersistenceProvider::setActiveMemFile(MemFilePtr ptr,
                                             const char* user) const
{
    LOG(spam, "Inserting active memfile %s for user %s",
        ptr->getFile().getBucketId().toString().c_str(),
        user);
    getThreadLocalMemFile() = ptr;
}

void
MemFilePersistenceProvider::clearActiveMemFile(spi::Context* context) const
{
    LOG(spam, "Clearing active memfile");
    MemFilePtr& ptr = getThreadLocalMemFile();
    assert(ptr.get() == NULL || !ptr->slotsAltered());
    ptr = MemFilePtr();
    if (context != 0) {
        TRACE(*context, 9, "clearActiveMemFile", "Done clearing");
    }
}

enum MemFileAccessGuardScopeExitAction {
    REINSERT_AS_ACTIVE = 0x1,
};

/**
 * The MemFile access guard provides a simple scope guard for providing
 * exception safety for operations toward MemFiles.
 * The guard will always evict a file from the cache iff the guard has not
 * been dismissed upon destruction. This will throw away all non-persisted
 * changes to file and clear it from the cache to force a full reload on next
 * access. This is the safest option, as all operations that are not yet
 * persisted should fail back to the client automatically.
 *
 * The current MemFile will be reinserted as the thread's active MemFile
 * iff REINSERT_AS_ACTIVE has specified as a guard construction flag and
 * the guard was dismissed before destruction.
 */
class MemFileAccessGuard : public Types
{
    MemFileAccessGuard(const MemFileAccessGuard&);
    MemFileAccessGuard& operator=(const MemFileAccessGuard&);
public:
    MemFileAccessGuard(const MemFilePersistenceProvider& spi,
                       const MemFilePtr& ptr,
                       const char* user,
                       uint32_t flags = 0)
        : _spi(spi),
          _ptr(ptr),
          _user(user),
          _flags(flags),
          _dismissed(false)
    {
        assert(_ptr.get());
    }

    ~MemFileAccessGuard() {
        if (!_dismissed) {
            LOG(debug,
                "Access guard in %s not dismissed on scope exit, clearing %s"
                " from cache to force reload of file on next access.",
                _user,
                _ptr->getFile().getBucketId().toString().c_str());

            _ptr->clearFlag(SLOTS_ALTERED);
            _ptr.eraseFromCache(); // nothrow
        }
        if ((_flags & REINSERT_AS_ACTIVE) && _dismissed) {
            _spi.setActiveMemFile(_ptr, _user);
        } else {
            _spi.clearActiveMemFile();
        }
    }

    // Misc accessors
    MemFile* operator->() {
        return _ptr.get();
    }
    MemFile& operator*() {
        return *_ptr;
    }
    const MemFile* operator->() const {
        return _ptr.get();
    }
    const MemFile& operator*() const {
        return *_ptr;
    }
    MemFilePtr& getMemFilePtr() {
        return _ptr;
    }
    const MemFilePtr& getMemFilePtr() const {
        return _ptr;
    }

    /**
     * If all access towards the MemFile has been successfully performed,
     * calling dismiss() will ensure that the specified cleanup actions
     * are not taken upon scope exit.
     */
    void dismiss() {
        _dismissed = true;
    }
    
private:
    const MemFilePersistenceProvider& _spi;
    MemFilePtr _ptr;
    const char* _user;
    const uint32_t _flags;
    bool _dismissed;
};

void
MemFilePersistenceProvider::handleBucketCorruption(const FileSpecification& file) const
{
    spi::Bucket fixBucket(document::Bucket(spi::FixedBucketSpaces::default_space(),
                                           file.getBucketId()),
                          spi::PartitionId(file.getDirectory().getIndex()));

    // const_cast is nasty, but maintain() must necessarily be able to
    // modify state...
    MemFilePersistenceProvider& mutableSelf(
            const_cast<MemFilePersistenceProvider&>(*this));
    
    spi::Result maintainResult(mutableSelf.maintain(fixBucket, spi::HIGH));
    if (maintainResult.getErrorCode() != spi::Result::NONE) {
        LOG(warning,
            "Failed to successfully repair %s after corruptions: %s",
            fixBucket.toString().c_str(),
            maintainResult.toString().c_str());
    }

    // Add bucket to set of modified buckets so service layer can request
    // new bucket info.
    _env->addModifiedBucket(file.getBucketId());
}

template<typename C>
C MemFilePersistenceProvider::handleException(const std::exception& e,
                                              bool canRepairBucket) const
{
    LOG(debug, "Handling exception caught during processing: %s", e.what());

    const MemFileIoException* io = dynamic_cast<const MemFileIoException*>(&e);
    if (io != NULL) {
        std::ostringstream error;
        error << "Exception caught processing operation for "
              << io->getFile().getPath() << ": " << io->getMessage();

        Device::State deviceState(
                mapIoExceptionToDeviceState(io->getType()));

        if (deviceState != Device::OK) {
            io->getFile().getDirectory().addEvent(
                    deviceState,
                    io->getMessage(),
                    VESPA_STRLOC);

            _env->_mountPoints->writeToFile();

            return C(spi::Result::FATAL_ERROR, error.str());
        }
        if (io->getType() == vespalib::IoException::CORRUPT_DATA
            && canRepairBucket)
        {
            handleBucketCorruption(io->getFile());
        }

        return C(spi::Result::TRANSIENT_ERROR, error.str());
    }
    const CorruptMemFileException* ce(
            dynamic_cast<const CorruptMemFileException*>(&e));
    if (ce != 0) {
        std::ostringstream error;
        error << "Exception caught processing operation for "
              << ce->getFile().getPath() << ": " << ce->getMessage();
        if (canRepairBucket) {
            handleBucketCorruption(ce->getFile());
        }
        return C(spi::Result::TRANSIENT_ERROR, error.str());
    }

    const TimestampExistException* ts =
        dynamic_cast<const TimestampExistException*>(&e);
    if (ts != NULL) {
        return C(spi::Result::TIMESTAMP_EXISTS, ts->getMessage());
    }

    return C(spi::Result::PERMANENT_ERROR, e.what());
}

MemFilePersistenceProvider::MemFilePersistenceProvider(
        framework::ComponentRegister& compReg,
        const config::ConfigUri & configUri)
    : framework::Component(compReg, "memfilepersistenceprovider"),
      framework::StatusReporter("memfilepersistenceprovider",
                                "VDS Persistence Provider"),
      _componentRegister(compReg),
      _configUri(configUri),
      _config(*config::ConfigGetter<vespa::config::storage::StorMemfilepersistenceConfig>::getConfig(configUri.getConfigId(),
                                                                                     configUri.getContext())),
      _memFileMapper(*this),
      _repo(0),
      _metrics(*this),
      _threadLocals(1024)
{
    registerMetric(_metrics);
    registerStatusPage(*this);
}

MemFilePersistenceProvider::~MemFilePersistenceProvider()
{
}

void
MemFilePersistenceProvider::setDocumentRepo(const document::DocumentTypeRepo& repo)
{
    _repo = &repo;
    if (_env.get()) {
        _env->setRepo(_repo);
    }
}

using MemFilePersistenceConfig
    = vespa::config::storage::StorMemfilepersistenceConfig;
using PersistenceConfig = vespa::config::content::PersistenceConfig;

namespace {

MemFileCache::MemoryUsage
getCacheLimits(const MemFilePersistenceConfig& cfg)
{
    MemFileCache::MemoryUsage cacheLimits;
    cacheLimits.metaSize = cfg.cacheSize * cfg.cacheSizeMetaPercentage / 100;
    cacheLimits.headerSize = cfg.cacheSize * cfg.cacheSizeHeaderPercentage / 100;
    cacheLimits.bodySize = cfg.cacheSize * cfg.cacheSizeBodyPercentage / 100;
    return cacheLimits;
}

std::unique_ptr<Options>
makeOptions(const MemFilePersistenceConfig& memFileCfg,
            const PersistenceConfig& persistenceCfg)
{
    return std::unique_ptr<Options>(new Options(memFileCfg, persistenceCfg));
}

}

void
MemFilePersistenceProvider::setConfig(std::unique_ptr<vespa::config::storage::StorMemfilepersistenceConfig> cfg)
{
    assert(cfg.get() != nullptr);
    auto guard = _env->acquireConfigWriteLock();

    guard.setMemFilePersistenceConfig(std::move(cfg));

    if (guard.hasPersistenceConfig()) {
        guard.setOptions(makeOptions(*guard.memFilePersistenceConfig(),
                                     *guard.persistenceConfig()));
    }

    // Data race free; acquires internal cache lock.
    _cache->setCacheSize(getCacheLimits(*guard.memFilePersistenceConfig()));
}

void
MemFilePersistenceProvider::setConfig(std::unique_ptr<vespa::config::content::PersistenceConfig> cfg)
{
    assert(cfg.get() != nullptr);
    auto guard = _env->acquireConfigWriteLock();

    guard.setPersistenceConfig(std::move(cfg));

    if (guard.hasMemFilePersistenceConfig()) {
        guard.setOptions(makeOptions(*guard.memFilePersistenceConfig(),
                                     *guard.persistenceConfig()));
    }
}

void
MemFilePersistenceProvider::setConfig(std::unique_ptr<vespa::config::storage::StorDevicesConfig> cfg)
{
    assert(cfg.get() != nullptr);
    auto guard = _env->acquireConfigWriteLock();
    guard.setDevicesConfig(std::move(cfg));
}

spi::PartitionStateListResult
MemFilePersistenceProvider::getPartitionStates() const
{
    // Lazily initialize to ensure service layer has set up enough for us
    // to use all we need (memory manager for instance)
    if (_env.get() == 0) {
        assert(_repo != 0);
        _cache.reset(new MemFileCache(_componentRegister,
                                      _metrics._cache));
        _cache->setCacheSize(getCacheLimits(_config));
        try{
            _env.reset(new Environment(
                    _configUri, *_cache, _memFileMapper, *_repo, getClock()));
        } catch (NoDisksException& e) {
            return spi::PartitionStateListResult(spi::PartitionStateList(
                    spi::PartitionId::Type(0)));
        }
        _fileScanner.reset(new FileScanner(
                    _componentRegister, *_env->_mountPoints,
                    _config.dirLevels, _config.dirSpread));
        _util.reset(new OperationHandler(*_env));
        _iteratorHandler.reset(new IteratorHandler(*_env));
        _joinOperationHandler.reset(new JoinOperationHandler(*_env));
        _splitOperationHandler.reset(new SplitOperationHandler(*_env));
    }
    return _env->_mountPoints->getPartitionStates();
}

spi::BucketIdListResult
MemFilePersistenceProvider::listBuckets(BucketSpace space, spi::PartitionId partition) const
{
    spi::BucketIdListResult::List buckets;
    if (space == spi::FixedBucketSpaces::default_space()) {
        _fileScanner->buildBucketList(buckets, partition, 0, 1);
    }
    return spi::BucketIdListResult(buckets);
}

spi::BucketIdListResult
MemFilePersistenceProvider::getModifiedBuckets(BucketSpace space) const
{
    document::BucketId::List modified;
    if (space == spi::FixedBucketSpaces::default_space()) {
        _env->swapModifiedBuckets(modified); // Atomic op
    }
    return spi::BucketIdListResult(modified);
}

spi::BucketInfoResult
MemFilePersistenceProvider::getBucketInfo(const spi::Bucket& bucket) const
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    LOG(spam, "getBucketInfo(%s)", bucket.toString().c_str());
    try {
        bool retainMemFile = hasCachedMemFile();
        MemFileAccessGuard file(*this,
                                getMemFile(bucket, false),
                                "getBucketInfo",
                                retainMemFile ? REINSERT_AS_ACTIVE : 0);

        spi::BucketInfo info = file->getBucketInfo();

        file.dismiss();
        return spi::BucketInfoResult(info);
    } catch (std::exception& e) {
        return handleException<spi::BucketInfoResult>(e, true);
    }
}

spi::Result
MemFilePersistenceProvider::put(const spi::Bucket& bucket, spi::Timestamp ts,
                                const document::Document::SP& doc,
                                spi::Context& context)
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "put");
    LOG(spam, "put(%s, %zu, %s)", bucket.toString().c_str(), uint64_t(ts),
        doc->getId().toString().c_str());
    try {
        TRACE(context, 9, "put", "Grabbing memfile"); 
        MemFileAccessGuard file(*this, getMemFile(bucket), "put",
                                REINSERT_AS_ACTIVE);
        TRACE(context, 9, "put", "Altering file in memory"); 
        _util->write(*file, *doc, Timestamp(ts));

        TRACE(context, 9, "put", "Dismissing file"); 
        file.dismiss();
        return spi::Result();
    } catch (std::exception& e) {
        return handleException<spi::Result>(e, true);
    }
}

spi::RemoveResult
MemFilePersistenceProvider::remove(const spi::Bucket& bucket, spi::Timestamp ts,
                                   const DocumentId& id, spi::Context& context)
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "remove");
    LOG(spam, "remove(%s, %zu, %s)", bucket.toString().c_str(), uint64_t(ts),
        id.toString().c_str());
    try {
        TRACE(context, 9, "remove", "Grabbing memfile"); 
        MemFileAccessGuard file(*this, getMemFile(bucket), "remove",
                                REINSERT_AS_ACTIVE);
        TRACE(context, 9, "remove", "Altering file in memory"); 
        spi::Timestamp oldTs(_util->remove(*file,
                id, Timestamp(ts),
                OperationHandler::ALWAYS_PERSIST_REMOVE).getTime());
        TRACE(context, 9, "remove", "Dismissing file"); 
        file.dismiss();
        return spi::RemoveResult(oldTs > 0);
    } catch (std::exception& e) {
        return handleException<spi::RemoveResult>(e, true);
    }
}

spi::RemoveResult
MemFilePersistenceProvider::removeIfFound(const spi::Bucket& bucket,
                                          spi::Timestamp ts,
                                          const DocumentId& id,
                                          spi::Context& context)
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "removeIfFound");
    LOG(spam, "removeIfFound(%s, %zu, %s)", bucket.toString().c_str(),
        uint64_t(ts), id.toString().c_str());
    try {
        TRACE(context, 9, "removeIfFound", "Grabbing memfile"); 
        MemFileAccessGuard file(*this, getMemFile(bucket), "removeiffound",
                                REINSERT_AS_ACTIVE);
        TRACE(context, 9, "removeIfFound", "Altering file in memory"); 
        spi::Timestamp oldTs(_util->remove(*file,
                    id, Timestamp(ts),
                    OperationHandler::PERSIST_REMOVE_IF_FOUND).getTime());
        TRACE(context, 9, "removeIfFound", "Dismissing file"); 
        file.dismiss();
        return spi::RemoveResult(oldTs > 0);
    } catch (std::exception& e) {
        return handleException<spi::RemoveResult>(e, true);
    }
}

spi::UpdateResult
MemFilePersistenceProvider::MemFilePersistenceProvider::update(
        const spi::Bucket& bucket, spi::Timestamp ts,
        const document::DocumentUpdate::SP& upd, spi::Context& context)
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "update");
    LOG(spam, "update(%s, %zu, %s)", bucket.toString().c_str(), uint64_t(ts),
        upd->getId().toString().c_str());
    try {
        TRACE(context, 9, "update", "Grabbing memfile"); 
        MemFileAccessGuard file(*this, getMemFile(bucket), "update",
                                REINSERT_AS_ACTIVE);
        TRACE(context, 9, "update", "Reading old entry"); 
        bool headerOnly = !upd->affectsDocumentBody();
        OperationHandler::ReadResult ret = _util->read(
                *file,
                upd->getId(),
                Timestamp(ts),
                headerOnly ? HEADER_ONLY : ALL);

        Document::UP doc = ret.getDoc();
        if (!doc.get()) {
            if (upd->getCreateIfNonExistent()) {
                TRACE(context, 9, "update", "Doc did not exist, creating one");
                doc.reset(new Document(upd->getType(), upd->getId()));
                upd->applyTo(*doc);
                _util->write(*file, *doc, Timestamp(ts));
                file.dismiss();
                return spi::UpdateResult(spi::Timestamp(ts));
            } else {
                TRACE(context, 9, "update", "Doc did not exist"); 
                file.dismiss();
                return spi::UpdateResult();
            }
        }

        if (Timestamp(ts) == ret._ts) {
            file.dismiss();
            if (doc->getId() == upd->getId()) {
                TRACE(context, 9, "update", "Timestamp exist same doc"); 
                return spi::UpdateResult(spi::Result::TRANSIENT_ERROR,
                                         "Update was already performed.");
            } else {
                // TODO: Assert-fail if we ever get here??
                TRACE(context, 9, "update", "Timestamp exist other doc");
                std::ostringstream error;
                error << "Update of " << upd->getId()
                      << ": There already exists a document"
                      << " with timestamp " << ts;

                return spi::UpdateResult(spi::Result::TIMESTAMP_EXISTS, error.str());
            }
        }

        TRACE(context, 9, "update", "Altering file in memory");
        upd->applyTo(*doc);
        if (headerOnly) {
            TRACE(context, 9, "update", "Writing new header entry");
            _util->update(*file, *doc, Timestamp(ts), Timestamp(ret._ts));
        } else {
            TRACE(context, 9, "update", "Writing new doc entry");
            _util->write(*file, *doc, Timestamp(ts));
        }
        if (headerOnly) {
            ++getMetrics().headerOnlyUpdates;
        }

        TRACE(context, 9, "update", "Dismissing file");
        file.dismiss();
        return spi::UpdateResult(spi::Timestamp(ret._ts.getTime()));
    } catch (std::exception& e) {
        return handleException<spi::UpdateResult>(e, true);
    }
}

spi::GetResult
MemFilePersistenceProvider::get(const spi::Bucket& bucket,
                                const document::FieldSet& fieldSet,
                                const DocumentId& id,
                                spi::Context& context) const
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "get");
    LOG(spam, "get(%s, %s)", bucket.toString().c_str(), id.toString().c_str());
    try {
        TRACE(context, 9, "get", "Grabbing memfile");
        MemFileAccessGuard file(*this, getMemFile(bucket), "get");
        document::HeaderFields headerFields;
        bool headerOnly = headerFields.contains(fieldSet);

        TRACE(context, 9, "get", "Reading from file.");
        OperationHandler::ReadResult ret =
            _util->read(*file, id, Timestamp(0),
                        headerOnly ? HEADER_ONLY : ALL);

        file.dismiss();
        if (!ret._doc.get()) {
            TRACE(context, 9, "get", "Doc not found");
            return spi::GetResult();
        }
        if (headerOnly) {
            TRACE(context, 9, "get", "Retrieved doc header only");
            ++getMetrics().headerOnlyGets;
        }
        // Don't create unnecessary copy if we want the full doc or header
        if (fieldSet.getType() == document::FieldSet::ALL
            || fieldSet.getType() == document::FieldSet::HEADER)
        {
            TRACE(context, 9, "get", "Returning doc");
            return spi::GetResult(ret.getDoc(), spi::Timestamp(ret._ts.getTime()));
        } else {
            TRACE(context, 9, "get", "Returning stripped doc");
            document::FieldSet::stripFields(*ret._doc, fieldSet);
            return spi::GetResult(ret.getDoc(), spi::Timestamp(ret._ts.getTime()));
        }
    } catch (std::exception& e) {
        return handleException<spi::GetResult>(e, true);
    }
}

spi::Result
MemFilePersistenceProvider::flush(const spi::Bucket& bucket,
                                  spi::Context& context)
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "flush");
    LOG(spam, "flush(%s)", bucket.toString().c_str());
    try {
        TRACE(context, 9, "flush", "Grabbing memfile");
        MemFileAccessGuard file(*this, getMemFile(bucket), "flush");

        LOG(spam, "Attempting to auto-flush %s",
            file->getFile().toString().c_str());
        TRACE(context, 9, "flush", "Flushing to disk");
        file->flushToDisk();

        TRACE(context, 9, "flush", "Dismissing file");
        file.dismiss();
        return spi::Result();
    } catch (std::exception& e) {
        return handleException<spi::Result>(e, true);
    }
}

spi::CreateIteratorResult
MemFilePersistenceProvider::createIterator(const spi::Bucket& b,
                                           const document::FieldSet& fieldSet,
                                           const spi::Selection& sel,
                                           spi::IncludedVersions versions,
                                           spi::Context& context)
{
    assert(b.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "createIterator");
    LOG(spam, "createIterator(%s)", b.toString().c_str());
    try {
        clearActiveMemFile();
        return _iteratorHandler->createIterator(b, fieldSet, sel, versions);
    } catch (std::exception& e) {
        return handleException<spi::CreateIteratorResult>(e, true);
    }
}

spi::IterateResult
MemFilePersistenceProvider::iterate(spi::IteratorId iterId,
                                    uint64_t maxByteSize,
                                    spi::Context& context) const
{
    TRACEGENERIC(context, "iterate");
    try {
        clearActiveMemFile(&context);
        spi::IterateResult result(
                _iteratorHandler->iterate(iterId, maxByteSize));
        TRACE(context, 9, "iterate", "Done filling iterator");
        return result;
    } catch (std::exception& e) {
        return handleException<spi::IterateResult>(e, true);
    }
}

spi::Result
MemFilePersistenceProvider::destroyIterator(spi::IteratorId iterId,
                                            spi::Context& context)
{
    TRACEGENERIC(context, "destroyIterator");
    try {
        return _iteratorHandler->destroyIterator(iterId);
    } catch (std::exception& e) {
        return handleException<spi::IterateResult>(e, true);
    }
}

spi::Result
MemFilePersistenceProvider::deleteBucket(const spi::Bucket& bucket,
                                         spi::Context& context)
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "deleteBucket");
    LOG(spam, "deleteBucket(%s)", bucket.toString().c_str());
    try {
        TRACE(context, 9, "deleteBucket", "Grabbing memfile");
        MemFileAccessGuard file(*this, getMemFile(bucket), "deleteBucket");
        TRACE(context, 9, "deleteBucket", "Deleting it");
        file.getMemFilePtr().deleteFile();
        // It is assumed guard will only kick in if deleteFile has failed
        // _before_ it erases the bucket from the cache (since this should
        // be a nothrow op). Otherwise, this will crash trying to deref a
        // null ptr.
        TRACE(context, 9, "deleteBucket", "Dismissing file");
        file.dismiss();
        return spi::Result();
    } catch (std::exception& e) {
        return handleException<spi::IterateResult>(e, true);
    }
}

spi::Result
MemFilePersistenceProvider::split(const spi::Bucket& source,
                                  const spi::Bucket& target1,
                                  const spi::Bucket& target2,
                                  spi::Context& context)
{
    assert(source.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    assert(target1.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    assert(target2.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "split");
    LOG(spam, "split(%s -> %s, %s)", source.toString().c_str(),
        target1.toString().c_str(), target2.toString().c_str());
    try {
        clearActiveMemFile();
        return _splitOperationHandler->split(source, target1, target2);
    } catch (std::exception& e) {
        return handleException<spi::Result>(e, true);
    }
}

spi::Result
MemFilePersistenceProvider::join(const spi::Bucket& source1,
                                 const spi::Bucket& source2,
                                 const spi::Bucket& target,
                                 spi::Context& context)
{
    assert(source1.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    assert(source2.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    assert(target.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "join");
    LOG(spam, "join(%s, %s -> %s)", source1.toString().c_str(),
        source2.toString().c_str(), target.toString().c_str());
    try {
        clearActiveMemFile();
        return _joinOperationHandler->join(source1, source2, target);
    } catch (std::exception& e) {
        return handleException<spi::Result>(e, true);
    }
}

spi::Result
MemFilePersistenceProvider::removeEntry(const spi::Bucket& bucket,
                                        spi::Timestamp ts,
                                        spi::Context& context)
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    TRACEGENERIC(context, "removeEntry");
    LOG(spam, "removeEntry(%s, %zu)", bucket.toString().c_str(), uint64_t(ts));
    try {
        TRACE(context, 9, "removeEntry", "Grabbing memfile");
        MemFileAccessGuard file(*this, getMemFile(bucket), "revert",
                                REINSERT_AS_ACTIVE);
        const MemSlot* slot = file->getSlotAtTime(Timestamp(ts));
        if (slot) {
            TRACE(context, 9, "removeEntry", "Removing slot");
            file->removeSlot(*slot);
        }

        TRACE(context, 9, "removeEntry", "Dismissing file");
        file.dismiss();
        return spi::Result();
    } catch (std::exception& e) {
        return handleException<spi::Result>(e, true);
    }
}

spi::Result
MemFilePersistenceProvider::maintain(const spi::Bucket& bucket,
                                     spi::MaintenanceLevel level)
{
    assert(bucket.getBucketSpace() == spi::FixedBucketSpaces::default_space());
    LOG(spam, "maintain(%s)", bucket.toString().c_str());
    try {
        MemFileAccessGuard file(*this, getMemFile(bucket, false), "maintain");
        assert(!file->slotsAltered());
        if (!file->fileExists()) {
            LOG(debug,
                "maintain(%s): file '%s' does not exist, nothing to maintain. "
                "Assuming file was corrupted and auto-deleted.",
                bucket.toString().c_str(),
                file->getFile().getPath().c_str());
            return spi::Result();
        }

        std::ostringstream report;
        const uint32_t verifyFlags((level == spi::HIGH) ? 0 : DONT_VERIFY_BODY);
        if (!file->repair(report, verifyFlags)) {
            LOG(debug,
                "repair() on %s indicated errors, evicting from cache to "
                "force reload of file with altered metadata",
                bucket.toString().c_str());
            return spi::Result(); // No dismissal of guard; auto-evict.
        }
        assert(!file->slotsAltered());
        file->compact();
        file->flushToDisk(CHECK_NON_DIRTY_FILE_FOR_SPACE);

        file.dismiss();
        return spi::Result();
    } catch (std::exception& e) {
        // Failing maintain() cannot cause an auto-repair since this will
        // in turn call maintain().
        return handleException<spi::Result>(e, false);
    }
}

vespalib::string
MemFilePersistenceProvider::getReportContentType(const framework::HttpUrlPath&) const
{
    return "text/html";
}

namespace {

void
printMemoryUsage(std::ostream& out,
                 const char* part,
                 uint64_t usage,
                 uint64_t total)
{
    out << "<li>" << part << ": " << usage;
    if (total > 0) {
        out << " (" << ((static_cast<double>(usage) / total) * 100.0) << "%)";
    }
    out << "</li>\n";
}

}

bool
MemFilePersistenceProvider::reportStatus(std::ostream& out,
                                         const framework::HttpUrlPath& path) const
{
    framework::PartlyHtmlStatusReporter htmlReporter(*this);
    htmlReporter.reportHtmlHeader(out, path);

    out << "<h1>Mem file persistence provider status page</h1>\n";
    bool printVerbose = path.hasAttribute("verbose");
    if (!printVerbose) {
        out << "<p><a href=\"memfilepersistenceprovider?verbose\">"
               "More verbose</a></p>\n";
    } else {
        out << "<p><a href=\"memfilepersistenceprovider\">"
               "Less verbose</a></p>\n";
    }

    MemFileCache::Statistics cacheStats(_env->_cache.getCacheStats());
    const MemFileCache::MemoryUsage& memUsage(cacheStats._memoryUsage);
    out << "<p>Cache with "  << cacheStats._numEntries
        << " entries using " << memUsage.sum()
        << " of max "        << cacheStats._cacheSize
        << " bytes</p>\n";
    out << "<ul>\n";
    printMemoryUsage(out, "Meta", memUsage.metaSize, memUsage.sum());
    printMemoryUsage(out, "Header", memUsage.headerSize, memUsage.sum());
    printMemoryUsage(out, "Body", memUsage.bodySize, memUsage.sum());
    out << "</ul>\n";
    out << "</p>\n";

    if (printVerbose) {
        _env->_cache.printCacheEntriesHtml(out);
    }

    htmlReporter.reportHtmlFooter(out, path);

    return true;
}

}
