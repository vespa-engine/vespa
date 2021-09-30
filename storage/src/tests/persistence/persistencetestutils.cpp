// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencetestutils.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/storage/persistence/filestorage/filestorhandlerimpl.h>
#include <vespa/storage/persistence/persistencehandler.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/config-stor-filestor.h>
#include <thread>

using document::DocumentType;
using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;

namespace storage {

namespace {

vdstestlib::DirConfig initialize(const std::string & rootOfRoot) {
    vdstestlib::DirConfig config(getStandardConfig(true, rootOfRoot));
    std::string rootFolder = getRootFolder(config);
    vespalib::rmdir(rootFolder, true);
    vespalib::mkdir(vespalib::make_string("%s/disks/d0", rootFolder.c_str()), true);
    return config;
}

template<typename T>
struct ConfigReader : public T::Subscriber
{
    T config;

    ConfigReader(const std::string& configId) {
        T::subscribe(configId, *this);
    }
    void configure(const T& c) { config = c; }
};

}

PersistenceTestEnvironment::PersistenceTestEnvironment(const std::string & rootOfRoot)
    : _config(initialize(rootOfRoot)),
      _messageKeeper(),
      _node(NodeIndex(0), _config.getConfigId()),
      _component(_node.getComponentRegister(), "persistence test env"),
      _metrics()
{
    _node.setupDummyPersistence();
    _metrics.initDiskMetrics(1, 1);
    _handler = std::make_unique<FileStorHandlerImpl>(_messageKeeper, _metrics, _node.getComponentRegister());
    _diskEnv = std::make_unique<PersistenceUtil>(_component, *_handler,
                                                 *_metrics.disk->threads[0], _node.getPersistenceProvider());
}

PersistenceTestEnvironment::~PersistenceTestEnvironment() {
    _handler->close();
    while (!_handler->closed()) {
        std::this_thread::sleep_for(1ms);
    }
}

PersistenceTestUtils::PersistenceTestUtils()
    : _env(std::make_unique<PersistenceTestEnvironment>("todo-make-unique-persistencetestutils")),
      _replySender(),
      _bucketOwnershipNotifier(getEnv()._component, getEnv()._fileStorHandler),
      _persistenceHandler()
{
    setupExecutor(1);
    vespa::config::content::StorFilestorConfig cfg;
    _persistenceHandler = std::make_unique<PersistenceHandler>(*_sequenceTaskExecutor, _env->_component, cfg,
                                                               getPersistenceProvider(), getEnv()._fileStorHandler,
                                                               _bucketOwnershipNotifier, getEnv()._metrics);
}
PersistenceTestUtils::~PersistenceTestUtils() = default;

std::string
PersistenceTestUtils::dumpBucket(const document::BucketId& bid) {
    return dynamic_cast<spi::dummy::DummyPersistence&>(_env->_node.getPersistenceProvider()).dumpBucket(makeSpiBucket(bid));
}

VESPA_THREAD_STACK_TAG(test_executor)

void
PersistenceTestUtils::setupExecutor(uint32_t numThreads) {
    _sequenceTaskExecutor = vespalib::SequencedTaskExecutor::create(test_executor, numThreads, 1000, vespalib::Executor::OptimizeFor::ADAPTIVE);
}

document::Document::SP
PersistenceTestUtils::schedulePut(
        uint32_t location,
        spi::Timestamp timestamp,
        uint32_t minSize,
        uint32_t maxSize)
{
    document::Document::SP doc(createRandomDocumentAtLocation(location, timestamp, minSize, maxSize));
    auto msg = std::make_shared<api::PutCommand>(makeDocumentBucket(document::BucketId(16, location)), doc, timestamp);
    fsHandler().schedule(msg);
    return doc;
}

StorBucketDatabase::WrappedEntry
PersistenceTestUtils::getBucket(const document::BucketId& id)
{
    return _env->_node.getStorageBucketDatabase().get(id, "foo");
}

StorBucketDatabase::WrappedEntry
PersistenceTestUtils::createBucket(const document::BucketId& id)
{
    return _env->_node.getStorageBucketDatabase().get(
            id,
            "foo",
            StorBucketDatabase::CREATE_IF_NONEXISTING);
}

spi::PersistenceProvider&
PersistenceTestUtils::getPersistenceProvider()
{
    return _env->_node.getPersistenceProvider();
}

std::string
PersistenceTestUtils::getBucketStatus(const document::BucketId& id)
{
    std::ostringstream ost;
    StorBucketDatabase::WrappedEntry entry(
            _env->_node.getStorageBucketDatabase().get(
                    id, "foo"));

    ost << id << ": ";
    if (!entry.exist()) {
        ost << "null";
    } else {
        ost << entry->getBucketInfo().getDocumentCount();
    }

    return ost.str();
}

document::Document::SP
PersistenceTestUtils::doPutOnDisk(
        uint32_t location,
        spi::Timestamp timestamp,
        uint32_t minSize,
        uint32_t maxSize)
{
    document::Document::SP doc(createRandomDocumentAtLocation(location, timestamp, minSize, maxSize));
    spi::Bucket b(makeSpiBucket(document::BucketId(16, location)));
    spi::Context context(spi::Priority(0), spi::Trace::TraceLevel(0));
    getPersistenceProvider().createBucket(b, context);
    getPersistenceProvider().put(spi::Bucket(b), timestamp, doc, context);
    return doc;
}

bool
PersistenceTestUtils::doRemoveOnDisk(
        const document::BucketId& bucketId,
        const document::DocumentId& docId,
        spi::Timestamp timestamp,
        bool persistRemove)
{
    spi::Context context(spi::Priority(0), spi::Trace::TraceLevel(0));
    if (persistRemove) {
        spi::RemoveResult result = getPersistenceProvider().removeIfFound(makeSpiBucket(bucketId),timestamp, docId, context);
        return result.wasFound();
    }
    spi::RemoveResult result = getPersistenceProvider().remove(makeSpiBucket(bucketId), timestamp, docId, context);

    return result.wasFound();
}

bool
PersistenceTestUtils::doUnrevertableRemoveOnDisk(
        const document::BucketId& bucketId,
        const document::DocumentId& docId,
        spi::Timestamp timestamp)
{
    spi::Context context(spi::Priority(0),spi::Trace::TraceLevel(0));
    spi::RemoveResult result = getPersistenceProvider().remove(makeSpiBucket(bucketId), timestamp, docId, context);
    return result.wasFound();
}

spi::GetResult
PersistenceTestUtils::doGetOnDisk(const document::BucketId& bucketId, const document::DocumentId& docId)
{
    auto fieldSet = std::make_unique<document::AllFields>();
    spi::Context context(spi::Priority(0), spi::Trace::TraceLevel(0));
    return getPersistenceProvider().get(makeSpiBucket(bucketId), *fieldSet, docId, context);
}

document::DocumentUpdate::SP
PersistenceTestUtils::createBodyUpdate(const document::DocumentId& docId, const document::FieldValue& updateValue)
{
    const DocumentType* docType(getTypeRepo()->getDocumentType("testdoctype1"));
    auto update = std::make_shared<document::DocumentUpdate>(*getTypeRepo(), *docType, docId);
    auto assignUpdate = std::make_shared<document::AssignValueUpdate>(updateValue);
    document::FieldUpdate fieldUpdate(docType->getField("content"));
    fieldUpdate.addUpdate(*assignUpdate);
    update->addUpdate(fieldUpdate);
    return update;
}

document::DocumentUpdate::SP
PersistenceTestUtils::createHeaderUpdate(const document::DocumentId& docId, const document::FieldValue& updateValue)
{
    const DocumentType* docType(getTypeRepo()->getDocumentType("testdoctype1"));
    auto update = std::make_shared<document::DocumentUpdate>(*getTypeRepo(), *docType, docId);
    auto assignUpdate = std::make_shared<document::AssignValueUpdate>(updateValue);
    document::FieldUpdate fieldUpdate(docType->getField("headerval"));
    fieldUpdate.addUpdate(*assignUpdate);
    update->addUpdate(fieldUpdate);
    return update;
}

void
PersistenceTestUtils::doPut(const document::Document::SP& doc, spi::Timestamp time, uint16_t usedBits)
{
    document::BucketId bucket(_env->_component.getBucketIdFactory().getBucketId(doc->getId()));
    bucket.setUsedBits(usedBits);
    doPut(doc, bucket, time);
}

void
PersistenceTestUtils::doPut(const document::Document::SP& doc, document::BucketId bid, spi::Timestamp time)
{
    spi::Bucket b(makeSpiBucket(bid));
    spi::Context context(spi::Priority(0), spi::Trace::TraceLevel(0));
    getPersistenceProvider().createBucket(b, context);
    getPersistenceProvider().put(b, time, std::move(doc), context);
}

spi::UpdateResult
PersistenceTestUtils::doUpdate(document::BucketId bid,
                               const document::DocumentUpdate::SP& update,
                               spi::Timestamp time)
{
    spi::Context context(spi::Priority(0), spi::Trace::TraceLevel(0));
    return getPersistenceProvider().update(makeSpiBucket(bid), time, update, context);
}

void
PersistenceTestUtils::doRemove(const document::DocumentId& id, spi::Timestamp time,
                               bool unrevertableRemove, uint16_t usedBits)
{
    document::BucketId bucket(
            _env->_component.getBucketIdFactory().getBucketId(id));
    bucket.setUsedBits(usedBits);
    spi::Context context(spi::Priority(0), spi::Trace::TraceLevel(0));
    if (unrevertableRemove) {
        getPersistenceProvider().remove(
                makeSpiBucket(bucket), time, id, context);
    } else {
        spi::RemoveResult result = getPersistenceProvider().removeIfFound(
                makeSpiBucket(bucket), time, id, context);
        if (!result.wasFound()) {
            throw vespalib::IllegalStateException(
                    "Attempted to remove non-existing doc " + id.toString(),
                    VESPA_STRLOC);
        }
    }
}

void
PersistenceTestUtils::clearBody(document::Document& doc)
{
    // FIXME(vekterli): temporary solution while we don't have
    // fieldset pruning functionality in Document.
    //doc->getBody().clear();
    vespalib::nbostream stream;
    doc.serializeHeader(stream);
    doc.deserialize(*getTypeRepo(), stream);
}

document::Document::UP
PersistenceTestUtils::createRandomDocumentAtLocation(
        uint64_t location, uint32_t seed,
        uint32_t minDocSize, uint32_t maxDocSize)
{
    return _env->_testDocMan.createRandomDocumentAtLocation(
            location, seed, minDocSize, maxDocSize);
}

void
PersistenceTestUtils::createTestBucket(const document::Bucket& bucket)
{
    document::BucketId bucketId(bucket.getBucketId());
    uint32_t opsPerType = 2;
    uint32_t numberOfLocations = 2;
    uint32_t minDocSize = 0;
    uint32_t maxDocSize = 128;
    for (uint32_t useHeaderOnly = 0; useHeaderOnly < 2; ++useHeaderOnly) {
        bool headerOnly = (useHeaderOnly == 1);
        for (uint32_t optype=0; optype < 4; ++optype) {
            for (uint32_t i=0; i<opsPerType; ++i) {
                uint32_t seed = useHeaderOnly * 10000 + optype * 1000 + i + 1;
                uint64_t location = (seed % numberOfLocations);
                location <<= 32;
                location += (bucketId.getRawId() & 0xffffffff);
                document::Document::SP doc(
                        createRandomDocumentAtLocation(location, seed, minDocSize, maxDocSize));
                if (headerOnly) {
                    clearBody(*doc);
                }
                doPut(doc, spi::Timestamp(seed), bucketId.getUsedBits());
                if (optype == 0) { // Regular put
                } else if (optype == 1) { // Overwritten later in time
                    document::Document::SP doc2(new document::Document(*doc));
                    doc2->setValue(doc2->getField("content"), document::StringFieldValue("overwritten"));
                    doPut(doc2, spi::Timestamp(seed + 500), bucketId.getUsedBits());
                } else if (optype == 2) { // Removed
                    doRemove(doc->getId(), spi::Timestamp(seed + 500), false, bucketId.getUsedBits());
                } else if (optype == 3) { // Unrevertable removed
                    doRemove(doc->getId(), spi::Timestamp(seed), true, bucketId.getUsedBits());
                }
            }
        }
    }
}

} // storage
