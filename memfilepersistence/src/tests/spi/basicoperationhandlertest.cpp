// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfiletestutils.h"
#include "simulatedfailurefile.h"
#include "options_builder.h"
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/persistence/spi/fixed_bucket_spaces.h>
#include <vespa/vdstestlib/cppunit/macros.h>

using storage::spi::test::makeSpiBucket;

namespace storage {
namespace memfile {
namespace {
    spi::LoadType defaultLoadType(0, "default");
}

class BasicOperationHandlerTest : public SingleDiskMemFileTestUtils
{
    CPPUNIT_TEST_SUITE(BasicOperationHandlerTest);
    CPPUNIT_TEST(testGetHeaderOnly);
    CPPUNIT_TEST(testGetFieldFiltering);
    CPPUNIT_TEST(testRemove);
    CPPUNIT_TEST(testRemoveWithNonMatchingTimestamp);
    CPPUNIT_TEST(testRemoveWithNonMatchingTimestampAlwaysPersist);
    CPPUNIT_TEST(testRemoveForExistingRemoveSameTimestamp);
    CPPUNIT_TEST(testRemoveForExistingRemoveNewTimestamp);
    CPPUNIT_TEST(testRemoveForExistingRemoveNewTimestampAlwaysPersist);
    CPPUNIT_TEST(testRemoveDocumentNotFound);
    CPPUNIT_TEST(testRemoveDocumentNotFoundAlwaysPersist);
    CPPUNIT_TEST(testRemoveExistingOlderDocumentVersion);
    CPPUNIT_TEST(testPutSameTimestampAsRemove);
    CPPUNIT_TEST(testUpdateBody);
    CPPUNIT_TEST(testUpdateHeaderOnly);
    CPPUNIT_TEST(testUpdateTimestampExists);
    CPPUNIT_TEST(testUpdateForNonExistentDocWillFail);
    CPPUNIT_TEST(testUpdateMayCreateDoc);
    CPPUNIT_TEST(testRemoveEntry);
    CPPUNIT_TEST(testEraseFromCacheOnFlushException);
    CPPUNIT_TEST(testEraseFromCacheOnMaintainException);
    CPPUNIT_TEST(testEraseFromCacheOnDeleteBucketException);
    CPPUNIT_TEST(list_buckets_returns_empty_set_for_non_default_bucketspace);
    CPPUNIT_TEST(get_modified_buckets_returns_empty_set_for_non_default_bucketspace);
    CPPUNIT_TEST_SUITE_END();

    void doTestRemoveDocumentNotFound(
            OperationHandler::RemoveType persistRemove);
    void doTestRemoveWithNonMatchingTimestamp(
            OperationHandler::RemoveType persistRemove);
    void doTestRemoveForExistingRemoveNewTimestamp(
            OperationHandler::RemoveType persistRemove);
public:
    void setupTestConfig();
    void testPutHeadersOnly();
    void testPutHeadersOnlyDocumentNotFound();
    void testPutHeadersOnlyTimestampNotFound();
    void testGetHeaderOnly();
    void testGetFieldFiltering();
    void testRemove();
    void testRemoveWithNonMatchingTimestamp();
    void testRemoveWithNonMatchingTimestampAlwaysPersist();
    void testRemoveForExistingRemoveSameTimestamp();
    void testRemoveForExistingRemoveNewTimestamp();
    void testRemoveForExistingRemoveNewTimestampAlwaysPersist();
    void testRemoveDocumentNotFound();
    void testRemoveDocumentNotFoundAlwaysPersist();
    void testRemoveExistingOlderDocumentVersion();
    void testPutSameTimestampAsRemove();
    void testUpdateBody();
    void testUpdateHeaderOnly();
    void testUpdateTimestampExists();
    void testUpdateForNonExistentDocWillFail();
    void testUpdateMayCreateDoc();
    void testRemoveEntry();
    void testEraseFromCacheOnFlushException();
    void testEraseFromCacheOnMaintainException();
    void testEraseFromCacheOnDeleteBucketException();
    void list_buckets_returns_empty_set_for_non_default_bucketspace();
    void get_modified_buckets_returns_empty_set_for_non_default_bucketspace();
};

CPPUNIT_TEST_SUITE_REGISTRATION(BasicOperationHandlerTest);

/**
 * Test that doing a header-only get gives back a document containing
 * only the document header
 */
void
BasicOperationHandlerTest::testGetHeaderOnly()
{
    document::BucketId bucketId(16, 4);

    Document::SP doc(createRandomDocumentAtLocation(4));
    doc->setValue(doc->getField("hstringval"), document::StringFieldValue("hypnotoad"));
    doc->setValue(doc->getField("headerval"), document::IntFieldValue(42));

    doPut(doc, bucketId, Timestamp(4567), 0);
    flush(bucketId);

    spi::GetResult reply = doGet(bucketId, doc->getId(), document::HeaderFields());

    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, reply.getErrorCode());
    CPPUNIT_ASSERT(reply.hasDocument());
    CPPUNIT_ASSERT_EQUAL(std::string("headerval: 42\nhstringval: hypnotoad\n"),
                         stringifyFields(reply.getDocument()));
    CPPUNIT_ASSERT_EQUAL(
            size_t(1),
            getPersistenceProvider().getMetrics().headerOnlyGets.getValue());
}

void
BasicOperationHandlerTest::testGetFieldFiltering()
{
    document::BucketId bucketId(16, 4);
    Document::SP doc(createRandomDocumentAtLocation(4));
    doc->setValue(doc->getField("headerval"), document::IntFieldValue(42));
    doc->setValue(doc->getField("hstringval"),
                  document::StringFieldValue("groovy"));

    document::FieldSetRepo repo;

    doPut(doc, bucketId, Timestamp(4567), 0);
    flush(bucketId);
    spi::GetResult reply(doGet(bucketId,
                               doc->getId(),
                               *repo.parse(*getTypeRepo(), "testdoctype1:hstringval")));
    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, reply.getErrorCode());
    CPPUNIT_ASSERT(reply.hasDocument());
    CPPUNIT_ASSERT_EQUAL(std::string("hstringval: groovy\n"),
                         stringifyFields(reply.getDocument()));
    CPPUNIT_ASSERT_EQUAL(
            size_t(1),
            getPersistenceProvider().getMetrics().headerOnlyGets.getValue());
}

void
BasicOperationHandlerTest::testRemove()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);

    document::Document::SP doc = doPut(4, Timestamp(1));

    CPPUNIT_ASSERT_EQUAL(true, doRemove(bucketId,
                                        doc->getId(),
                                        Timestamp(2),
                                        OperationHandler::PERSIST_REMOVE_IF_FOUND));

    getPersistenceProvider().flush(makeSpiBucket(bucketId), context);

    env()._cache.clear();

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(2), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT_EQUAL(*doc, *file->getDocument((*file)[0], ALL));

    CPPUNIT_ASSERT_EQUAL(Timestamp(2), (*file)[1].getTimestamp());
    CPPUNIT_ASSERT((*file)[1].deleted());
    CPPUNIT_ASSERT_EQUAL(DataLocation(0, 0), (*file)[1].getLocation(BODY));
    CPPUNIT_ASSERT_EQUAL((*file)[0].getLocation(HEADER),
                         (*file)[1].getLocation(HEADER));
}

/**
 * Test that removing a document with a max timestamp for which there
 * is no matching document does not add a remove slot to the memfile
 */
void
BasicOperationHandlerTest::doTestRemoveWithNonMatchingTimestamp(
        OperationHandler::RemoveType persistRemove)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);
    document::Document::SP doc = doPut(4, Timestamp(1234));

    CPPUNIT_ASSERT_EQUAL(false, doRemove(bucketId,
                                         doc->getId(),
                                         Timestamp(1233),
                                         persistRemove));

    getPersistenceProvider().flush(makeSpiBucket(bucketId), context);

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(
            uint32_t(persistRemove == OperationHandler::ALWAYS_PERSIST_REMOVE
                     ? 2 : 1),
            file->getSlotCount());

    int i = 0;
    if (persistRemove == OperationHandler::ALWAYS_PERSIST_REMOVE) {
        CPPUNIT_ASSERT_EQUAL(Timestamp(1233), (*file)[0].getTimestamp());
        CPPUNIT_ASSERT((*file)[0].deleted());
        CPPUNIT_ASSERT_EQUAL(DataLocation(0, 0), (*file)[0].getLocation(BODY));
        CPPUNIT_ASSERT((*file)[0].getLocation(HEADER)
                       != (*file)[1].getLocation(HEADER));
        CPPUNIT_ASSERT_EQUAL(doc->getId(), file->getDocumentId((*file)[0]));
        ++i;
    }

    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), (*file)[i].getTimestamp());
    CPPUNIT_ASSERT(!(*file)[i].deleted());
    CPPUNIT_ASSERT(file->getDocument((*file)[i], ALL)->getValue("content").get());
}

/**
 * Test that removing a document with a max timestamp for which there
 * is no matching document does not add a remove slot to the memfile
 */
void
BasicOperationHandlerTest::testRemoveWithNonMatchingTimestamp()
{
    doTestRemoveWithNonMatchingTimestamp(
            OperationHandler::PERSIST_REMOVE_IF_FOUND);
}

void
BasicOperationHandlerTest::testRemoveWithNonMatchingTimestampAlwaysPersist()
{
    doTestRemoveWithNonMatchingTimestamp(
            OperationHandler::ALWAYS_PERSIST_REMOVE);
}

/**
 * Test that doing a remove with a timestamp for which there already
 * exists a remove does not add another remove slot
 */
void
BasicOperationHandlerTest::testRemoveForExistingRemoveSameTimestamp()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);
    document::Document::SP doc = doPut(4, Timestamp(1234));

    CPPUNIT_ASSERT_EQUAL(true, doRemove(bucketId,
                                        doc->getId(),
                                        Timestamp(1235),
                                        OperationHandler::PERSIST_REMOVE_IF_FOUND));
    CPPUNIT_ASSERT_EQUAL(false, doRemove(bucketId,
                                         doc->getId(),
                                         Timestamp(1235),
                                         OperationHandler::PERSIST_REMOVE_IF_FOUND));

    getPersistenceProvider().flush(makeSpiBucket(bucketId), context);

    // Should only be one remove entry still
    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(2), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT(file->getDocument((*file)[0], ALL)->getValue("content").get());

    CPPUNIT_ASSERT_EQUAL(Timestamp(1235), (*file)[1].getTimestamp());
    CPPUNIT_ASSERT((*file)[1].deleted());
}

void
BasicOperationHandlerTest::doTestRemoveForExistingRemoveNewTimestamp(
        OperationHandler::RemoveType persistRemove)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);
    document::Document::SP doc = doPut(4, Timestamp(1234));

    CPPUNIT_ASSERT_EQUAL(true, doRemove(bucketId,
                                        doc->getId(),
                                        Timestamp(1235),
                                        OperationHandler::PERSIST_REMOVE_IF_FOUND));
    CPPUNIT_ASSERT_EQUAL(false, doRemove(bucketId,
                                        doc->getId(),
                                        Timestamp(1236),
                                        persistRemove));

    getPersistenceProvider().flush(makeSpiBucket(bucketId), context);

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(
            uint32_t(persistRemove == OperationHandler::ALWAYS_PERSIST_REMOVE
                     ? 3 : 2),
            file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT(file->getDocument((*file)[0], ALL)->getValue("content").get());

    CPPUNIT_ASSERT_EQUAL(Timestamp(1235), (*file)[1].getTimestamp());
    CPPUNIT_ASSERT((*file)[1].deleted());

    if (persistRemove == OperationHandler::ALWAYS_PERSIST_REMOVE) {
        CPPUNIT_ASSERT_EQUAL(Timestamp(1236), (*file)[2].getTimestamp());
        CPPUNIT_ASSERT((*file)[2].deleted());
    }
}

/**
 * Test that doing a second remove with a newer timestamp does not add
 * another remove slot when PERSIST_REMOVE_IF_FOUND is specified
 */
void
BasicOperationHandlerTest::testRemoveForExistingRemoveNewTimestamp()
{
    doTestRemoveForExistingRemoveNewTimestamp(
            OperationHandler::PERSIST_REMOVE_IF_FOUND);
}

void
BasicOperationHandlerTest::testRemoveForExistingRemoveNewTimestampAlwaysPersist()
{
    doTestRemoveForExistingRemoveNewTimestamp(
            OperationHandler::ALWAYS_PERSIST_REMOVE);
}

/**
 * Test removing an older version of a document. Older version should be removed
 * in-place without attempting to add a new slot (which would fail).
 */
void
BasicOperationHandlerTest::testRemoveExistingOlderDocumentVersion()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);
    document::Document::SP doc = doPut(4, Timestamp(1234));

    CPPUNIT_ASSERT_EQUAL(true, doRemove(bucketId,
                                        doc->getId(),
                                        Timestamp(1235),
                                        OperationHandler::ALWAYS_PERSIST_REMOVE));

    getPersistenceProvider().flush(makeSpiBucket(bucketId), context);

    CPPUNIT_ASSERT_EQUAL(true, doRemove(bucketId,
                                        doc->getId(),
                                        Timestamp(1234),
                                        OperationHandler::ALWAYS_PERSIST_REMOVE));

    getPersistenceProvider().flush(makeSpiBucket(bucketId), context);

    // Should now be two remove entries.
    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(2), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT_EQUAL(doc->getId(), file->getDocumentId((*file)[0]));
    CPPUNIT_ASSERT((*file)[0].deleted());

    CPPUNIT_ASSERT_EQUAL(Timestamp(1235), (*file)[1].getTimestamp());
    CPPUNIT_ASSERT_EQUAL(doc->getId(), file->getDocumentId((*file)[1]));
    CPPUNIT_ASSERT((*file)[1].deleted());
}

void
BasicOperationHandlerTest::doTestRemoveDocumentNotFound(
        OperationHandler::RemoveType persistRemove)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);
    document::DocumentId docId("userdoc:test:4:0");
    doPut(4, Timestamp(1234));

    CPPUNIT_ASSERT_EQUAL(false,
                         doRemove(bucketId,
                                  docId,
                                  Timestamp(1235),
                                  persistRemove));

    getPersistenceProvider().flush(makeSpiBucket(bucketId), context);

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(
            uint32_t(persistRemove == OperationHandler::ALWAYS_PERSIST_REMOVE
                     ? 2 : 1),
            file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), (*file)[0].getTimestamp());
    if (persistRemove == OperationHandler::ALWAYS_PERSIST_REMOVE) {
        CPPUNIT_ASSERT_EQUAL(Timestamp(1235), (*file)[1].getTimestamp());
        CPPUNIT_ASSERT((*file)[1].deleted());
        CPPUNIT_ASSERT_EQUAL(docId, file->getDocumentId((*file)[1]));
    }
/* TODO: Test this in service layer tests.
    CPPUNIT_ASSERT_EQUAL(
            uint64_t(1),
            env()._metrics.remove[documentapi::LoadType::DEFAULT].notFound.getValue());
*/
}

/**
 * Test that removing a non-existing document when PERSIST_EXISTING_ONLY is
 * specified does not add a remove entry
 */
void
BasicOperationHandlerTest::testRemoveDocumentNotFound()
{
    doTestRemoveDocumentNotFound(
            OperationHandler::PERSIST_REMOVE_IF_FOUND);
}

void
BasicOperationHandlerTest::testRemoveDocumentNotFoundAlwaysPersist()
{
    doTestRemoveDocumentNotFound(
            OperationHandler::ALWAYS_PERSIST_REMOVE);
}

void
BasicOperationHandlerTest::testPutSameTimestampAsRemove()
{
    document::BucketId bucketId(16, 4);

    document::Document::SP doc = doPut(4, Timestamp(1234));

    CPPUNIT_ASSERT_EQUAL(true, doRemove(bucketId,
                                        doc->getId(),
                                        Timestamp(1235),
                                        OperationHandler::PERSIST_REMOVE_IF_FOUND));

    // Flush here to avoid put+remove being thrown away by duplicate timestamp
    // exception evicting the cache and unpersisted changes.
    flush(bucketId);

    doPut(4, Timestamp(1235));
    flush(bucketId);

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(2), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT(file->getDocument((*file)[0], ALL)->getValue("content").get());

    CPPUNIT_ASSERT_EQUAL(Timestamp(1235), (*file)[1].getTimestamp());
    CPPUNIT_ASSERT((*file)[1].deleted());
}

/**
 * Test that updating body results in a new memfile slot containing
 * an updated document
 */
void
BasicOperationHandlerTest::testUpdateBody()
{
    document::BucketId bucketId(16, 4);
    document::StringFieldValue updateValue("foo");
    document::Document::SP doc = doPut(4, Timestamp(1234));
    document::Document originalDoc(*doc);

    document::DocumentUpdate::SP update = createBodyUpdate(
            doc->getId(), updateValue);

    spi::UpdateResult result = doUpdate(bucketId, update, Timestamp(5678));
    flush(bucketId);
    CPPUNIT_ASSERT_EQUAL(1234, (int)result.getExistingTimestamp());

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(2), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT(file->getDocument((*file)[0], ALL)->getValue("content").get());
    CPPUNIT_ASSERT_EQUAL(*(originalDoc.getValue("content")),
                         *file->getDocument((*file)[0], ALL)->getValue("content"));

    CPPUNIT_ASSERT_EQUAL(Timestamp(5678), (*file)[1].getTimestamp());
    CPPUNIT_ASSERT(file->getDocument((*file)[1], ALL)->getValue("content").get());
    CPPUNIT_ASSERT_EQUAL(updateValue,
                         dynamic_cast<document::StringFieldValue&>(
                                 *file->getDocument((*file)[1], ALL)->getValue(
                                         "content")));
    CPPUNIT_ASSERT_EQUAL(
            size_t(0),
            getPersistenceProvider().getMetrics().headerOnlyUpdates.getValue());
}

void
BasicOperationHandlerTest::testUpdateHeaderOnly()
{
    document::BucketId bucketId(16, 4);
    document::IntFieldValue updateValue(42);
    document::Document::SP doc = doPut(4, Timestamp(1234));

    document::DocumentUpdate::SP update = createHeaderUpdate(
            doc->getId(), updateValue);

    spi::UpdateResult result = doUpdate(bucketId, update, Timestamp(5678));
    flush(bucketId);
    CPPUNIT_ASSERT_EQUAL(1234, (int)result.getExistingTimestamp());

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(2), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT(file->getDocument((*file)[0], ALL)->getValue("headerval").get() ==
                   NULL);

    CPPUNIT_ASSERT_EQUAL(Timestamp(5678), (*file)[1].getTimestamp());
    CPPUNIT_ASSERT(file->getDocument((*file)[1], ALL)->getValue("headerval").get());
    CPPUNIT_ASSERT_EQUAL(updateValue,
                         dynamic_cast<document::IntFieldValue&>(
                                 *file->getDocument((*file)[1], ALL)->getValue(
                                         "headerval")));
    CPPUNIT_ASSERT_EQUAL(
            size_t(1),
            getPersistenceProvider().getMetrics().headerOnlyUpdates.getValue());
}

void
BasicOperationHandlerTest::testUpdateTimestampExists()
{
    document::BucketId bucketId(16, 4);
    document::IntFieldValue updateValue(42);
    document::Document::SP doc = doPut(4, Timestamp(1234));

    document::DocumentUpdate::SP update = createHeaderUpdate(
            doc->getId(), updateValue);

    spi::UpdateResult result = doUpdate(bucketId, update, Timestamp(1234));
    flush(bucketId);
    CPPUNIT_ASSERT_EQUAL(spi::Result::TRANSIENT_ERROR, result.getErrorCode());
}

void
BasicOperationHandlerTest::testUpdateForNonExistentDocWillFail()
{
    document::BucketId bucketId(16, 4);
    document::IntFieldValue updateValue(42);
    Timestamp timestamp(5678);

    // Is there an easier way to get a DocumentId?
    document::Document::UP doc(
            createRandomDocumentAtLocation(4, timestamp.getTime()));
    const DocumentId& documentId = doc->getId();

    document::DocumentUpdate::SP update = createHeaderUpdate(
            documentId, updateValue);

    spi::UpdateResult result = doUpdate(bucketId, update, timestamp);
    flush(bucketId);
    CPPUNIT_ASSERT_EQUAL(0, (int)result.getExistingTimestamp());

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(0), file->getSlotCount());
}

void
BasicOperationHandlerTest::testUpdateMayCreateDoc()
{
    document::BucketId bucketId(16, 4);
    document::IntFieldValue updateValue(42);
    Timestamp timestamp(5678);

    // Is there an easier way to get a DocumentId?
    document::Document::UP doc(
            createRandomDocumentAtLocation(4, timestamp.getTime()));
    const DocumentId& documentId = doc->getId();

    document::DocumentUpdate::SP update = createHeaderUpdate(
            documentId, updateValue);
    update->setCreateIfNonExistent(true);

    spi::UpdateResult result = doUpdate(bucketId, update, timestamp);
    flush(bucketId);
    CPPUNIT_ASSERT_EQUAL(timestamp.getTime(),
                         (uint64_t)result.getExistingTimestamp());

    MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(timestamp, (*file)[0].getTimestamp());

    auto headerval = file->getDocument((*file)[0], ALL)->getValue("headerval");
    CPPUNIT_ASSERT(headerval.get() != nullptr);
    CPPUNIT_ASSERT_EQUAL(updateValue,
                         dynamic_cast<document::IntFieldValue&>(*headerval));
}

void
BasicOperationHandlerTest::testRemoveEntry()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);

    doPut(4, Timestamp(1234));
    Document::SP doc = doPut(4, Timestamp(2345));
    doPut(4, Timestamp(3456));

    getPersistenceProvider().removeEntry(makeSpiBucket(bucketId), spi::Timestamp(1234), context);
    getPersistenceProvider().removeEntry(makeSpiBucket(bucketId), spi::Timestamp(3456), context);
    flush(bucketId);

    memfile::MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(2345), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT_EQUAL(*doc, *file->getDocument((*file)[0], ALL));
}

void
BasicOperationHandlerTest::setupTestConfig()
{
    using MemFileConfig = vespa::config::storage::StorMemfilepersistenceConfig; 
    using MemFileConfigBuilder
        = vespa::config::storage::StorMemfilepersistenceConfigBuilder;
    MemFileConfigBuilder builder(
            *env().acquireConfigReadLock().memFilePersistenceConfig());
    builder.minimumFileMetaSlots = 2;
    builder.minimumFileHeaderBlockSize = 3000;
    auto newConfig = std::unique_ptr<MemFileConfig>(new MemFileConfig(builder));
    env().acquireConfigWriteLock().setMemFilePersistenceConfig(
            std::move(newConfig));
}

void
BasicOperationHandlerTest::testEraseFromCacheOnFlushException()
{
    document::BucketId bucketId(16, 4);

    setupTestConfig();

    document::Document::SP doc(
            createRandomDocumentAtLocation(4, 2345, 1024, 1024));
    doPut(doc, bucketId, Timestamp(2345));
    flush(bucketId);
    // Must throw out cache to re-create lazyfile
    env()._cache.clear();

    env()._lazyFileFactory =
        std::unique_ptr<Environment::LazyFileFactory>(
                new SimulatedFailureLazyFile::Factory);

    // Try partial write, followed by full rewrite
    for (int i = 0; i < 2; ++i) {
        for (int j = 0; j < i+1; ++j) {
            document::Document::SP doc2(
                    createRandomDocumentAtLocation(4, 4000 + j, 1500, 1500));
            doPut(doc2, bucketId, Timestamp(4000 + j));
        }
        spi::Result result = flush(bucketId);
        CPPUNIT_ASSERT(result.hasError());
        CPPUNIT_ASSERT(result.getErrorMessage().find("A simulated I/O write")
                       != vespalib::string::npos);

        CPPUNIT_ASSERT(!env()._cache.contains(bucketId));

        // Check that we still have first persisted put
        memfile::MemFilePtr file(getMemFile(bucketId));
        CPPUNIT_ASSERT_EQUAL(uint32_t(1), file->getSlotCount());
        CPPUNIT_ASSERT_EQUAL(Timestamp(2345), (*file)[0].getTimestamp());
        CPPUNIT_ASSERT_EQUAL(*doc, *file->getDocument((*file)[0], ALL));
    }
}

void
BasicOperationHandlerTest::testEraseFromCacheOnMaintainException()
{
    document::BucketId bucketId(16, 4);

    setupTestConfig();

    getFakeClock()._absoluteTime = framework::MicroSecTime(2000 * 1000000);
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options)
                .revertTimePeriod(framework::MicroSecTime(100000ULL * 1000000))
                .build());
    // Put a doc twice to allow for revert time compaction to be done
    document::Document::SP doc1(
            createRandomDocumentAtLocation(4, 2345, 1024, 1024));
    document::Document::SP doc2(
            createRandomDocumentAtLocation(4, 2345, 1024, 1024));
    doPut(doc1, bucketId, Timestamp(1000 * 1000000));
    doPut(doc2, bucketId, Timestamp(1500 * 1000000));
    flush(bucketId);
    env()._cache.clear();

    options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options)
                .revertTimePeriod(framework::MicroSecTime(100ULL * 1000000))
                .build());

    env()._lazyFileFactory =
        std::unique_ptr<Environment::LazyFileFactory>(
                new SimulatedFailureLazyFile::Factory);

        spi::Result result = getPersistenceProvider().maintain(makeSpiBucket(bucketId), spi::HIGH);
        CPPUNIT_ASSERT(result.hasError());
        CPPUNIT_ASSERT(result.getErrorMessage().find("A simulated I/O write")
                       != vespalib::string::npos);

    CPPUNIT_ASSERT(!env()._cache.contains(bucketId));

    // Check that we still have both persisted puts
    memfile::MemFilePtr file(getMemFile(bucketId));
    CPPUNIT_ASSERT_EQUAL(uint32_t(2), file->getSlotCount());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1000 * 1000000), (*file)[0].getTimestamp());
    CPPUNIT_ASSERT_EQUAL(*doc1, *file->getDocument((*file)[0], ALL));
    CPPUNIT_ASSERT_EQUAL(Timestamp(1500 * 1000000), (*file)[1].getTimestamp());
    CPPUNIT_ASSERT_EQUAL(*doc2, *file->getDocument((*file)[1], ALL));
}

void
BasicOperationHandlerTest::testEraseFromCacheOnDeleteBucketException()
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketId bucketId(16, 4);
    document::Document::SP doc(
            createRandomDocumentAtLocation(4, 2345, 1024, 1024));
    doPut(doc, bucketId, Timestamp(2345));
    flush(bucketId);
    env()._cache.clear();

    SimulatedFailureLazyFile::Factory* factory(
            new SimulatedFailureLazyFile::Factory);
    factory->setReadOpsBeforeFailure(0);
    env()._lazyFileFactory =
        std::unique_ptr<Environment::LazyFileFactory>(factory);

    // loadFile will fail
    spi::Result result = getPersistenceProvider().deleteBucket(makeSpiBucket(bucketId), context);
    CPPUNIT_ASSERT(result.hasError());
    CPPUNIT_ASSERT(result.getErrorMessage().find("A simulated I/O read")
                   != vespalib::string::npos);

    CPPUNIT_ASSERT(!env()._cache.contains(bucketId));

}

void BasicOperationHandlerTest::list_buckets_returns_empty_set_for_non_default_bucketspace() {
    document::BucketId bucket(16, 4);
    doPut(createRandomDocumentAtLocation(4), bucket, Timestamp(4567), 0);
    flush(bucket);

    auto buckets = getPersistenceProvider().listBuckets(spi::FixedBucketSpaces::global_space(), spi::PartitionId(0));
    CPPUNIT_ASSERT_EQUAL(size_t(0), buckets.getList().size());
}

void BasicOperationHandlerTest::get_modified_buckets_returns_empty_set_for_non_default_bucketspace() {
    env().addModifiedBucket(document::BucketId(16, 1234));
    auto buckets = getPersistenceProvider().getModifiedBuckets(spi::FixedBucketSpaces::global_space());
    CPPUNIT_ASSERT_EQUAL(size_t(0), buckets.getList().size());
}

}

}
