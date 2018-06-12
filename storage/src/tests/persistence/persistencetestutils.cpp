// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencetestutils.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>

using document::DocumentType;
using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;

namespace storage {

namespace {

    spi::LoadType defaultLoadType(0, "default");

    vdstestlib::DirConfig initialize(uint32_t numDisks, const std::string & rootOfRoot) {
        vdstestlib::DirConfig config(getStandardConfig(true, rootOfRoot));
        std::string rootFolder = getRootFolder(config);
        system(vespalib::make_string("rm -rf %s", rootFolder.c_str()).c_str());
        for (uint32_t i = 0; i < numDisks; i++) {
            system(vespalib::make_string("mkdir -p %s/disks/d%d", rootFolder.c_str(), i).c_str());
        }
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

PersistenceTestEnvironment::PersistenceTestEnvironment(DiskCount numDisks, const std::string & rootOfRoot)
    : _config(initialize(numDisks, rootOfRoot)),
      _messageKeeper(),
      _node(numDisks, NodeIndex(0), _config.getConfigId()),
      _component(_node.getComponentRegister(), "persistence test env"),
      _metrics(_component.getLoadTypes()->getMetricLoadTypes())
{
    _node.setupDummyPersistence();
    _metrics.initDiskMetrics(numDisks, _node.getLoadTypes()->getMetricLoadTypes(), 1, 1);
    _handler.reset(new FileStorHandler(_messageKeeper, _metrics,
                                       _node.getPersistenceProvider().getPartitionStates().getList(),
                                       _node.getComponentRegister()));
    for (uint32_t i = 0; i < numDisks; i++) {
        _diskEnvs.push_back(
                std::make_unique<PersistenceUtil>(_config.getConfigId(), _node.getComponentRegister(), *_handler,
                                                  *_metrics.disks[i]->threads[0], i, _node.getPersistenceProvider()));
    }
}

PersistenceTestUtils::PersistenceTestUtils()
{
}

PersistenceTestUtils::~PersistenceTestUtils()
{
}

std::string
PersistenceTestUtils::dumpBucket(const document::BucketId& bid, uint16_t disk) {
    return dynamic_cast<spi::dummy::DummyPersistence&>(_env->_node.getPersistenceProvider()).dumpBucket(makeSpiBucket(bid, spi::PartitionId(disk)));
}

void
PersistenceTestUtils::setupDisks(uint32_t numDisks) {
    _env.reset(new PersistenceTestEnvironment(DiskCount(numDisks), "todo-make-unique-persistencetestutils"));
}

std::unique_ptr<PersistenceThread>
PersistenceTestUtils::createPersistenceThread(uint32_t disk)
{
    return std::make_unique<PersistenceThread>(_env->_node.getComponentRegister(), _env->_config.getConfigId(),
                                               getPersistenceProvider(), getEnv()._fileStorHandler,
                                               getEnv()._metrics, disk);
}

document::Document::SP
PersistenceTestUtils::schedulePut(
        uint32_t location,
        spi::Timestamp timestamp,
        uint16_t disk,
        uint32_t minSize,
        uint32_t maxSize)
{
    document::Document::SP doc(createRandomDocumentAtLocation(
            location, timestamp, minSize, maxSize));
    std::shared_ptr<api::StorageMessage> msg(
            new api::PutCommand(
                makeDocumentBucket(document::BucketId(16, location)), doc, timestamp));
    fsHandler().schedule(msg, disk);
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
        ost << entry->getBucketInfo().getDocumentCount() << "," << entry->disk;
    }

    return ost.str();
}

document::Document::SP
PersistenceTestUtils::doPutOnDisk(
        uint16_t disk,
        uint32_t location,
        spi::Timestamp timestamp,
        uint32_t minSize,
        uint32_t maxSize)
{
    document::Document::SP doc(createRandomDocumentAtLocation(
                             location, timestamp, minSize, maxSize));
    spi::Bucket b(makeSpiBucket(document::BucketId(16, location), spi::PartitionId(disk)));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));

    getPersistenceProvider().createBucket(b, context);

    getPersistenceProvider().put(spi::Bucket(b), timestamp, doc, context);

    getPersistenceProvider().flush(b, context);
    return doc;
}

bool
PersistenceTestUtils::doRemoveOnDisk(
        uint16_t disk,
        const document::BucketId& bucketId,
        const document::DocumentId& docId,
        spi::Timestamp timestamp,
        bool persistRemove)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    if (persistRemove) {
        spi::RemoveResult result = getPersistenceProvider().removeIfFound(
            makeSpiBucket(bucketId, spi::PartitionId(disk)),
            timestamp, docId, context);
        return result.wasFound();
    }
    spi::RemoveResult result = getPersistenceProvider().remove(
            makeSpiBucket(bucketId, spi::PartitionId(disk)),
            timestamp, docId, context);

    return result.wasFound();
}

bool
PersistenceTestUtils::doUnrevertableRemoveOnDisk(
        uint16_t disk,
        const document::BucketId& bucketId,
        const document::DocumentId& docId,
        spi::Timestamp timestamp)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    spi::RemoveResult result = getPersistenceProvider().remove(
            makeSpiBucket(bucketId, spi::PartitionId(disk)),
            timestamp, docId, context);
    return result.wasFound();
}

spi::GetResult
PersistenceTestUtils::doGetOnDisk(
        uint16_t disk,
        const document::BucketId& bucketId,
        const document::DocumentId& docId,
        bool headerOnly)
{
    document::FieldSet::UP fieldSet(new document::AllFields());
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    if (headerOnly) {
        fieldSet.reset(new document::HeaderFields());
    }
    return getPersistenceProvider().get(makeSpiBucket(
            bucketId, spi::PartitionId(disk)), *fieldSet, docId, context);
}

document::DocumentUpdate::SP
PersistenceTestUtils::createBodyUpdate(
        const document::DocumentId& docId,
        const document::FieldValue& updateValue)
{
    const DocumentType* docType(_env->_component.getTypeRepo()
            ->getDocumentType("testdoctype1"));
    document::DocumentUpdate::SP update(
            new document::DocumentUpdate(*docType, docId));
    std::shared_ptr<document::AssignValueUpdate> assignUpdate(
            new document::AssignValueUpdate(updateValue));
    document::FieldUpdate fieldUpdate(docType->getField("content"));
    fieldUpdate.addUpdate(*assignUpdate);
    update->addUpdate(fieldUpdate);
    return update;
}

document::DocumentUpdate::SP
PersistenceTestUtils::createHeaderUpdate(
        const document::DocumentId& docId,
        const document::FieldValue& updateValue)
{
    const DocumentType* docType(_env->_component.getTypeRepo()
            ->getDocumentType("testdoctype1"));
    document::DocumentUpdate::SP update(
            new document::DocumentUpdate(*docType, docId));
    std::shared_ptr<document::AssignValueUpdate> assignUpdate(
            new document::AssignValueUpdate(updateValue));
    document::FieldUpdate fieldUpdate(docType->getField("headerval"));
    fieldUpdate.addUpdate(*assignUpdate);
    update->addUpdate(fieldUpdate);
    return update;
}

uint16_t
PersistenceTestUtils::getDiskFromBucketDatabaseIfUnset(const document::Bucket& bucket,
                                                       uint16_t disk)
{
    if (disk == 0xffff) {
        StorBucketDatabase::WrappedEntry entry(
                getEnv().getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(), "createTestBucket"));
        if (entry.exist()) {
            return entry->disk;
        } else {
            std::ostringstream error;
            error << bucket.toString() << " not in db and disk unset";
            throw vespalib::IllegalStateException(error.str(), VESPA_STRLOC);
        }
    }
    return disk;
}

void
PersistenceTestUtils::doPut(const document::Document::SP& doc,
                            spi::Timestamp time,
                            uint16_t disk,
                            uint16_t usedBits)
{
    document::BucketId bucket(
            _env->_component.getBucketIdFactory().getBucketId(doc->getId()));
    bucket.setUsedBits(usedBits);
    disk = getDiskFromBucketDatabaseIfUnset(makeDocumentBucket(bucket), disk);

    doPut(doc, bucket, time, disk);
}

void
PersistenceTestUtils::doPut(const document::Document::SP& doc,
                            document::BucketId bid,
                            spi::Timestamp time,
                            uint16_t disk)
{
    spi::Bucket b(makeSpiBucket(bid, spi::PartitionId(disk)));
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().createBucket(b, context);
    getPersistenceProvider().put(b, time, doc, context);
}

spi::UpdateResult
PersistenceTestUtils::doUpdate(document::BucketId bid,
                               const document::DocumentUpdate::SP& update,
                               spi::Timestamp time,
                               uint16_t disk)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    return getPersistenceProvider().update(
            makeSpiBucket(bid, spi::PartitionId(disk)), time, update, context);
}

void
PersistenceTestUtils::doRemove(const document::DocumentId& id, spi::Timestamp time,
                           uint16_t disk, bool unrevertableRemove,
                           uint16_t usedBits)
{
    document::BucketId bucket(
            _env->_component.getBucketIdFactory().getBucketId(id));
    bucket.setUsedBits(usedBits);
    disk = getDiskFromBucketDatabaseIfUnset(makeDocumentBucket(bucket), disk);
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    if (unrevertableRemove) {
        getPersistenceProvider().remove(
                makeSpiBucket(bucket, spi::PartitionId(disk)), time, id, context);
    } else {
        spi::RemoveResult result = getPersistenceProvider().removeIfFound(
                makeSpiBucket(bucket, spi::PartitionId(disk)), time, id, context);
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
    doc.deserialize(*_env->_component.getTypeRepo(), stream);
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
PersistenceTestUtils::createTestBucket(const document::Bucket& bucket,
                                       uint16_t disk)
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
                        createRandomDocumentAtLocation(
                            location, seed, minDocSize, maxDocSize));
                if (headerOnly) {
                    clearBody(*doc);
                }
                doPut(doc, spi::Timestamp(seed), disk, bucketId.getUsedBits());
                if (optype == 0) { // Regular put
                } else if (optype == 1) { // Overwritten later in time
                    document::Document::SP doc2(new document::Document(*doc));
                    doc2->setValue(doc2->getField("content"),
                                   document::StringFieldValue("overwritten"));
                    doPut(doc2, spi::Timestamp(seed + 500),
                          disk, bucketId.getUsedBits());
                } else if (optype == 2) { // Removed
                    doRemove(doc->getId(), spi::Timestamp(seed + 500), disk, false,
                             bucketId.getUsedBits());
                } else if (optype == 3) { // Unrevertable removed
                    doRemove(doc->getId(), spi::Timestamp(seed), disk, true,
                             bucketId.getUsedBits());
                }
            }
        }
    }
}

} // storage
