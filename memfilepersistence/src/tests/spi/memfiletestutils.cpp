// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/documenttype.h>
#include <vespa/memfilepersistence/spi/memfilepersistenceprovider.h>
#include <tests/spi/memfiletestutils.h>
#include <tests/spi/simulatedfailurefile.h>
#include <vespa/memfilepersistence/memfile/memfilecache.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sys/time.h>

using document::DocumentType;
using document::test::makeBucketSpace;
using storage::spi::test::makeSpiBucket;

namespace storage {
namespace memfile {

namespace {
    spi::LoadType defaultLoadType(0, "default");
}

namespace {
    vdstestlib::DirConfig initialize(uint32_t numDisks) {
        system(vespalib::make_string("rm -rf vdsroot").c_str());
        for (uint32_t i = 0; i < numDisks; i++) {
            system(vespalib::make_string("mkdir -p vdsroot/disks/d%d", i).c_str());
        }
        vdstestlib::DirConfig config(getStandardConfig(true));
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

MemFileTestEnvironment::MemFileTestEnvironment(
        uint32_t numDisks,
        framework::ComponentRegister& reg,
        const document::DocumentTypeRepo& repo)
    : _config(initialize(numDisks)),
      _provider(reg, _config.getConfigId())
{
    _provider.setDocumentRepo(repo);
    _provider.getPartitionStates();
}

MemFileTestUtils::MemFileTestUtils()
{
}

MemFileTestUtils::~MemFileTestUtils()
{
}

void
MemFileTestUtils::setupDisks(uint32_t numDisks) {
    tearDown();
    _componentRegister.reset(
            new framework::defaultimplementation::ComponentRegisterImpl);
    _clock.reset(new FakeClock);
    _componentRegister->setClock(*_clock);
    _env.reset(new MemFileTestEnvironment(numDisks,
                                          *_componentRegister,
                                          *getTypeRepo()));
}

Environment&
MemFileTestUtils::env()
{
    return static_cast<MemFilePersistenceProvider&>(
            getPersistenceProvider()).getEnvironment();
}

MemFilePersistenceProvider&
MemFileTestUtils::getPersistenceProvider()
{
    return _env->_provider;
}

MemFilePersistenceThreadMetrics&
MemFileTestUtils::getMetrics()
{
    return getPersistenceProvider().getMetrics();
}

std::string
MemFileTestUtils::getMemFileStatus(const document::BucketId& id,
                                   uint32_t disk)
{
    MemFilePtr file(getMemFile(id, disk));
    std::ostringstream ost;
    ost << id << ": " << file->getSlotCount() << "," << file->getDisk();
    return ost.str();
}

std::string
MemFileTestUtils::getModifiedBuckets()
{
    spi::BucketIdListResult result(
            getPersistenceProvider().getModifiedBuckets(makeBucketSpace()));
    const spi::BucketIdListResult::List& list(result.getList());
    std::ostringstream ss;
    for (size_t i = 0; i < list.size(); ++i) {
        if (i != 0) {
            ss << ",";
        }
        ss << std::hex << list[i].getId();
    }
    return ss.str();
}

MemFilePtr
MemFileTestUtils::getMemFile(const document::BucketId& id, uint16_t disk)
{
    return env()._cache.get(id, env(), env().getDirectory(disk));
}

spi::Result
MemFileTestUtils::flush(const document::BucketId& id, uint16_t disk)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    return getPersistenceProvider().flush(
            makeSpiBucket(id, spi::PartitionId(disk)), context);
}

document::Document::SP
MemFileTestUtils::doPutOnDisk(
        uint16_t disk,
        uint32_t location,
        Timestamp timestamp,
        uint32_t minSize,
        uint32_t maxSize)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::Document::SP doc(createRandomDocumentAtLocation(
                             location, timestamp.getTime(), minSize, maxSize));
    getPersistenceProvider().put(
            makeSpiBucket(document::BucketId(16, location), spi::PartitionId(disk)),
            spi::Timestamp(timestamp.getTime()),
            doc,
            context);
    return doc;
}

bool
MemFileTestUtils::doRemoveOnDisk(
        uint16_t disk,
        const document::BucketId& bucketId,
        const document::DocumentId& docId,
        Timestamp timestamp,
        OperationHandler::RemoveType persistRemove)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    if (persistRemove == OperationHandler::PERSIST_REMOVE_IF_FOUND) {
        spi::RemoveResult result = getPersistenceProvider().removeIfFound(
            makeSpiBucket(bucketId, spi::PartitionId(disk)),
            spi::Timestamp(timestamp.getTime()),
            docId,
            context);
        return result.wasFound();
    }
    spi::RemoveResult result = getPersistenceProvider().remove(
            makeSpiBucket(bucketId, spi::PartitionId(disk)),
            spi::Timestamp(timestamp.getTime()),
            docId,
            context);

    return result.wasFound();
}

bool
MemFileTestUtils::doUnrevertableRemoveOnDisk(
        uint16_t disk,
        const document::BucketId& bucketId,
        const DocumentId& docId,
        Timestamp timestamp)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    spi::RemoveResult result =
        getPersistenceProvider().remove(
                makeSpiBucket(bucketId, spi::PartitionId(disk)),
                spi::Timestamp(timestamp.getTime()),
                docId, context);

    return result.wasFound();
}

spi::GetResult
MemFileTestUtils::doGetOnDisk(
        uint16_t disk,
        const document::BucketId& bucketId,
        const document::DocumentId& docId,
        const document::FieldSet& fields)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    return getPersistenceProvider().get(
            makeSpiBucket(bucketId, spi::PartitionId(disk)),
            fields, docId, context);
}

document::DocumentUpdate::SP
MemFileTestUtils::createBodyUpdate(
        const document::DocumentId& docId,
        const document::FieldValue& updateValue)
{
    const DocumentType*
        docType(getTypeRepo()->getDocumentType("testdoctype1"));
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
MemFileTestUtils::createHeaderUpdate(
        const document::DocumentId& docId,
        const document::FieldValue& updateValue)
{
    const DocumentType*
        docType(getTypeRepo()->getDocumentType("testdoctype1"));
    document::DocumentUpdate::SP update(
            new document::DocumentUpdate(*docType, docId));
    std::shared_ptr<document::AssignValueUpdate> assignUpdate(
            new document::AssignValueUpdate(updateValue));
    document::FieldUpdate fieldUpdate(docType->getField("headerval"));
    fieldUpdate.addUpdate(*assignUpdate);
    update->addUpdate(fieldUpdate);
    return update;
}

void
MemFileTestUtils::doPut(const document::Document::SP& doc,
                        Timestamp time,
                        uint16_t disk,
                        uint16_t usedBits)
{
    document::BucketId bucket(
            getBucketIdFactory().getBucketId(doc->getId()));
    bucket.setUsedBits(usedBits);
    doPut(doc, bucket, time, disk);
}

void
MemFileTestUtils::doPut(const document::Document::SP& doc,
                        document::BucketId bid,
                        Timestamp time,
                        uint16_t disk)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    getPersistenceProvider().put(makeSpiBucket(bid, spi::PartitionId(disk)),
                                 spi::Timestamp(time.getTime()), doc, context);
}

spi::UpdateResult
MemFileTestUtils::doUpdate(document::BucketId bid,
                           const document::DocumentUpdate::SP& update,
                           Timestamp time,
                           uint16_t disk)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    return getPersistenceProvider().update(
            makeSpiBucket(bid, spi::PartitionId(disk)),
            spi::Timestamp(time.getTime()), update, context);
}

void
MemFileTestUtils::doRemove(const document::DocumentId& id, Timestamp time,
                           uint16_t disk, bool unrevertableRemove,
                           uint16_t usedBits)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucket(getBucketIdFactory().getBucketId(id));
    bucket.setUsedBits(usedBits);

    if (unrevertableRemove) {
        getPersistenceProvider().remove(
                makeSpiBucket(bucket, spi::PartitionId(disk)),
                spi::Timestamp(time.getTime()),
                id, context);
    } else {
        spi::RemoveResult result = getPersistenceProvider().removeIfFound(
                makeSpiBucket(bucket, spi::PartitionId(disk)),
                spi::Timestamp(time.getTime()),
                id, context);

        if (!result.wasFound()) {
            throw vespalib::IllegalStateException(
                    "Attempted to remove non-existing doc " + id.toString(),
                    VESPA_STRLOC);
        }
    }
}

void
MemFileTestUtils::copyHeader(document::Document& dest,
                             const document::Document& src)
{
    // FIXME(vekterli): temporary solution while we don't have
    // fieldset pruning functionality in Document.
    //dest.setHeaderPtr(src.getHeaderPtr());
    vespalib::nbostream originalBodyStream;
    dest.serializeBody(originalBodyStream);

    vespalib::nbostream headerStream;
    src.serializeHeader(headerStream);
    document::ByteBuffer hbuf(headerStream.peek(), headerStream.size());
    dest.deserializeHeader(*getTypeRepo(), hbuf);
    // deserializeHeader clears fields struct, so have to re-set body
    document::ByteBuffer bbuf(originalBodyStream.peek(),
                              originalBodyStream.size());
    dest.deserializeBody(*getTypeRepo(), bbuf);
}

void
MemFileTestUtils::copyBody(document::Document& dest,
                           const document::Document& src)
{
    // FIXME(vekterli): temporary solution while we don't have
    // fieldset pruning functionality in Document.
    //dest.setBodyPtr(src.getBodyPtr());
    vespalib::nbostream stream;
    src.serializeBody(stream);
    document::ByteBuffer buf(stream.peek(), stream.size());
    dest.deserializeBody(*getTypeRepo(), buf);
}

void
MemFileTestUtils::clearBody(document::Document& doc)
{
    // FIXME(vekterli): temporary solution while we don't have
    // fieldset pruning functionality in Document.
    //doc->getBody().clear();
    vespalib::nbostream stream;
    doc.serializeHeader(stream);
    doc.deserialize(*getTypeRepo(), stream);
}

void
MemFileTestUtils::createTestBucket(const document::BucketId& bucket,
                                   uint16_t disk)
{

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
                location += (bucket.getRawId() & 0xffffffff);
                document::Document::SP doc(
                        createRandomDocumentAtLocation(
                            location, seed, minDocSize, maxDocSize));
                if (headerOnly) {
                    clearBody(*doc);
                }
                doPut(doc, Timestamp(seed), disk, bucket.getUsedBits());
                if (optype == 0) { // Regular put
                } else if (optype == 1) { // Overwritten later in time
                    Document::SP doc2(new Document(*doc));
                    doc2->setValue(doc2->getField("content"),
                                   document::StringFieldValue("overwritten"));
                    doPut(doc2, Timestamp(seed + 500),
                          disk, bucket.getUsedBits());
                } else if (optype == 2) { // Removed
                    doRemove(doc->getId(), Timestamp(seed + 500), disk, false,
                             bucket.getUsedBits());
                } else if (optype == 3) { // Unrevertable removed
                    doRemove(doc->getId(), Timestamp(seed), disk, true,
                             bucket.getUsedBits());
                }
            }
        }
    }
    flush(bucket, disk);
}

void
MemFileTestUtils::simulateIoErrorsForSubsequentlyOpenedFiles(
        const IoErrors& errs)
{
    std::unique_ptr<SimulatedFailureLazyFile::Factory> factory(
            new SimulatedFailureLazyFile::Factory);
    factory->setWriteOpsBeforeFailure(errs._afterWrites);
    factory->setReadOpsBeforeFailure(errs._afterReads);
    env()._lazyFileFactory = std::move(factory);
}

void
MemFileTestUtils::unSimulateIoErrorsForSubsequentlyOpenedFiles()
{
    env()._lazyFileFactory = std::unique_ptr<Environment::LazyFileFactory>(
            new DefaultLazyFileFactory(0));
}

std::string
MemFileTestUtils::stringifyFields(const document::Document& doc) const
{
    using namespace document;
    std::vector<std::string> output;
    const StructFieldValue& fields(doc.getFields());
    for (StructFieldValue::const_iterator
             it(fields.begin()), e(fields.end());
         it != e; ++it)
    {
        std::ostringstream ss;
        const Field& f(it.field());
        ss << f.getName() << ": ";
        FieldValue::UP val(fields.getValue(f));
        if (val.get()) {
            ss << val->toString();
        } else {
            ss << "(null)";
        }
        output.push_back(ss.str());
    }
    std::ostringstream ret;
    std::sort(output.begin(), output.end());
    std::copy(output.begin(), output.end(),
              std::ostream_iterator<std::string>(ret, "\n"));
    return ret.str();
}

} // memfile
} // storage
