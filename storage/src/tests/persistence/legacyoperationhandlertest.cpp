// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/documentapi/loadtypes/loadtype.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/persistencetestutils.h>

using document::DocumentTypeRepo;
using document::TestDocRepo;

namespace storage {

class LegacyOperationHandlerTest : public SingleDiskPersistenceTestUtils
{
    CPPUNIT_TEST_SUITE(LegacyOperationHandlerTest);
    CPPUNIT_TEST(testMultioperationSingleBodyPut);
    CPPUNIT_TEST(testMultioperationSingleRemove);
    CPPUNIT_TEST(testMultioperationSingleUpdate);
    CPPUNIT_TEST(testMultioperationUpdateNotFound);
    CPPUNIT_TEST(testMultioperationMixedOperations);
    CPPUNIT_TEST_SUITE_END();

public:
    void setUp() override {
        SingleDiskPersistenceTestUtils::setUp();
        createBucket(document::BucketId(16, 4));
        spi::Context context(spi::LoadType(0, "default"), spi::Priority(0),
                             spi::Trace::TraceLevel(0));
        getPersistenceProvider().createBucket(
                spi::Bucket(document::BucketId(16, 4), spi::PartitionId(0)),
                context);
    }

    std::string stat() {
        return dumpBucket(document::BucketId(16, 4), 0);
    }

    void testMultioperationSingleBodyPut();
    void testMultioperationSingleRemove();
    void testMultioperationSingleUpdate();
    void testMultioperationUpdateNotFound();
    void testMultioperationMixedOperations();
    void testMultioperationMixedOperationsWrongBucket();
};

CPPUNIT_TEST_SUITE_REGISTRATION(LegacyOperationHandlerTest);

void
LegacyOperationHandlerTest::testMultioperationSingleBodyPut()
{
    std::unique_ptr<PersistenceThread> thread(createPersistenceThread(0));
    document::BucketId bucketId(16, 4);

    document::Document::SP doc(createRandomDocumentAtLocation(4, 1234, 0, 128));

    std::vector<char> buffer(1024);
    vdslib::WritableDocumentList block(getTypeRepo(), &buffer[0], buffer.size());
    block.addPut(*doc, api::Timestamp(1234));

    api::MultiOperationCommand cmd(getTypeRepo(), bucketId, 0);
    cmd.setOperations(block);

    thread->handleMultiOperation(cmd);

    CPPUNIT_ASSERT_EQUAL(
            std::string("DocEntry(1234, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"), stat());
}

void
LegacyOperationHandlerTest::testMultioperationSingleRemove()
{
    std::unique_ptr<PersistenceThread> thread(createPersistenceThread(0));
    document::BucketId bucketId(16, 4);

    document::Document::SP doc = doPut(4, spi::Timestamp(1234));

    std::vector<char> buffer(1024);
    vdslib::WritableDocumentList block(getTypeRepo(), &buffer[0], buffer.size());
    block.addRemove(doc->getId(), spi::Timestamp(1235));

    api::MultiOperationCommand cmd(getTypeRepo(), bucketId, 0);
    cmd.setOperations(block);

    thread->handleMultiOperation(cmd);

    CPPUNIT_ASSERT_EQUAL(
            std::string("DocEntry(1234, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"
                        "DocEntry(1235, 1, id:mail:testdoctype1:n=4:3619.html)\n"), stat());
}

void
LegacyOperationHandlerTest::testMultioperationSingleUpdate()
{
    std::unique_ptr<PersistenceThread> thread(createPersistenceThread(0));
    document::BucketId bucketId(16, 4);
    document::StringFieldValue updateValue("foo");

    document::Document::SP doc = doPut(4, spi::Timestamp(1234));
    document::Document originalDoc(*doc);

    document::DocumentUpdate::SP update = createBodyUpdate(
            doc->getId(), updateValue);

    std::vector<char> buffer(1024);
    vdslib::WritableDocumentList block(getTypeRepo(), &buffer[0], buffer.size());
    block.addUpdate(*update, api::Timestamp(1235));

    api::MultiOperationCommand cmd(getTypeRepo(), bucketId, 0);
    cmd.setOperations(block);

    thread->handleMultiOperation(cmd);

    CPPUNIT_ASSERT_EQUAL(
            std::string("DocEntry(1234, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"
                        "DocEntry(1235, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"), stat());
}

void
LegacyOperationHandlerTest::testMultioperationUpdateNotFound()
{
    std::unique_ptr<PersistenceThread> thread(createPersistenceThread(0));
    document::BucketId bucketId(16, 4);
    document::DocumentId docId("userdoc:test:4:0");
    document::StringFieldValue updateValue("foo");

    document::DocumentUpdate::SP update = createBodyUpdate(
            docId, updateValue);

    std::vector<char> buffer(1024);
    vdslib::WritableDocumentList block(getTypeRepo(), &buffer[0], buffer.size());
    block.addUpdate(*update, api::Timestamp(1235));

    api::MultiOperationCommand cmd(getTypeRepo(), bucketId, 0);
    cmd.setOperations(block);

    thread->handleMultiOperation(cmd);

    CPPUNIT_ASSERT_EQUAL(std::string(""), stat());
}

void
LegacyOperationHandlerTest::testMultioperationMixedOperations()
{
    std::unique_ptr<PersistenceThread> thread(createPersistenceThread(0));
    document::BucketId bucketId(16, 4);
    document::StringFieldValue updateValue("bar");

    document::Document::SP originalUpdateDoc = doPut(4, spi::Timestamp(1234));
    document::Document::SP originalRemoveDoc = doPut(4, spi::Timestamp(2345));

    document::DocumentUpdate::SP update = createBodyUpdate(
            originalUpdateDoc->getId(), updateValue);

    document::DocumentUpdate::SP nonExistingUpdate = createBodyUpdate(
            document::DocumentId("id:test:testdoctype1:n=4:nonexisting1"), updateValue);

    document::Document::SP putDoc(createRandomDocumentAtLocation(4, 5678, 0, 128));

    std::vector<char> buffer(1024);
    vdslib::WritableDocumentList block(getTypeRepo(), &buffer[0], buffer.size());

    block.addUpdate(*update, api::Timestamp(3456));
    block.addUpdate(*nonExistingUpdate, api::Timestamp(3457));
    block.addRemove(originalRemoveDoc->getId(), api::Timestamp(4567));
    block.addRemove(document::DocumentId("id:test:testdoctype1:n=4:nonexisting2"),
                    api::Timestamp(4568));
    block.addPut(*putDoc, api::Timestamp(5678));

    api::MultiOperationCommand cmd(getTypeRepo(), bucketId, 0);
    cmd.setOperations(block);

    thread->handleMultiOperation(cmd);

    CPPUNIT_ASSERT_EQUAL(
            std::string("DocEntry(1234, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"
                        "DocEntry(2345, 0, Doc(id:mail:testdoctype1:n=4:4008.html))\n"
                        "DocEntry(3456, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"
                        "DocEntry(4567, 1, id:mail:testdoctype1:n=4:4008.html)\n"
                        "DocEntry(4568, 1, id:test:testdoctype1:n=4:nonexisting2)\n"
                        "DocEntry(5678, 0, Doc(id:mail:testdoctype1:n=4:5177.html))\n"),
            stat());
}

}
