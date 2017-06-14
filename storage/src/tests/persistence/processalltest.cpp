// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/storage/persistence/processallhandler.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/documentapi/loadtypes/loadtype.h>
#include <tests/persistence/persistencetestutils.h>

namespace storage {

class ProcessAllHandlerTest : public SingleDiskPersistenceTestUtils
{
    CPPUNIT_TEST_SUITE(ProcessAllHandlerTest);
    CPPUNIT_TEST(testRemoveLocation);
    CPPUNIT_TEST(testRemoveLocationDocumentSubset);
    CPPUNIT_TEST(testRemoveLocationUnknownDocType);
    CPPUNIT_TEST(testRemoveLocationBogusSelection);
    CPPUNIT_TEST(testStat);
    CPPUNIT_TEST(testStatWithRemove);
    CPPUNIT_TEST(testStatWholeBucket);
    CPPUNIT_TEST_SUITE_END();

public:
    void testRemoveLocation();
    void testRemoveLocationDocumentSubset();
    void testRemoveLocationUnknownDocType();
    void testRemoveLocationEmptySelection();
    void testRemoveLocationBogusSelection();
    void testStat();
    void testStatWithRemove();
    void testStatWholeBucket();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ProcessAllHandlerTest);

void
ProcessAllHandlerTest::testRemoveLocation()
{
    document::BucketId bucketId(16, 4);
    doPut(4, spi::Timestamp(1234));
    doPut(4, spi::Timestamp(2345));

    api::RemoveLocationCommand removeLocation("id.user == 4", bucketId);
    ProcessAllHandler handler(getEnv(), getPersistenceProvider());
    spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
    handler.handleRemoveLocation(removeLocation, context);

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "DocEntry(1234, 1, id:mail:testdoctype1:n=4:3619.html)\n"
                    "DocEntry(2345, 1, id:mail:testdoctype1:n=4:4008.html)\n"),
            dumpBucket(bucketId));
}

void
ProcessAllHandlerTest::testRemoveLocationDocumentSubset()
{
    document::BucketId bucketId(16, 4);
    ProcessAllHandler handler(getEnv(), getPersistenceProvider());

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i), 0);
    }

    api::RemoveLocationCommand
        removeLocation("testdoctype1.headerval % 2 == 0", bucketId);
    spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
    handler.handleRemoveLocation(removeLocation, context);

    CPPUNIT_ASSERT_EQUAL(
            std::string("DocEntry(100, 1, id:mail:testdoctype1:n=4:3619.html)\n"
                        "DocEntry(101, 0, Doc(id:mail:testdoctype1:n=4:33113.html))\n"
                        "DocEntry(102, 1, id:mail:testdoctype1:n=4:62608.html)\n"
                        "DocEntry(103, 0, Doc(id:mail:testdoctype1:n=4:26566.html))\n"
                        "DocEntry(104, 1, id:mail:testdoctype1:n=4:56061.html)\n"
                        "DocEntry(105, 0, Doc(id:mail:testdoctype1:n=4:20019.html))\n"
                        "DocEntry(106, 1, id:mail:testdoctype1:n=4:49514.html)\n"
                        "DocEntry(107, 0, Doc(id:mail:testdoctype1:n=4:13472.html))\n"
                        "DocEntry(108, 1, id:mail:testdoctype1:n=4:42967.html)\n"
                        "DocEntry(109, 0, Doc(id:mail:testdoctype1:n=4:6925.html))\n"),
            dumpBucket(bucketId));
}

void
ProcessAllHandlerTest::testRemoveLocationUnknownDocType()
{
    document::BucketId bucketId(16, 4);
    doPut(4, spi::Timestamp(1234));

    api::RemoveLocationCommand
        removeLocation("unknowndoctype.headerval % 2 == 0", bucketId);

    bool gotException = false;
    try {
        ProcessAllHandler handler(getEnv(), getPersistenceProvider());
        spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
        handler.handleRemoveLocation(removeLocation, context);
    } catch (...) {
        gotException = true;
    }
    CPPUNIT_ASSERT(gotException);

    CPPUNIT_ASSERT_EQUAL(
            std::string("DocEntry(1234, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"),
            dumpBucket(bucketId));
}

void
ProcessAllHandlerTest::testRemoveLocationBogusSelection()
{
    document::BucketId bucketId(16, 4);
    doPut(4, spi::Timestamp(1234));

    api::RemoveLocationCommand removeLocation("id.bogus != badgers", bucketId);

    bool gotException = false;
    try {
        ProcessAllHandler handler(getEnv(), getPersistenceProvider());
        spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
        handler.handleRemoveLocation(removeLocation, context);
    } catch (...) {
        gotException = true;
    }
    CPPUNIT_ASSERT(gotException);

    CPPUNIT_ASSERT_EQUAL(
            std::string("DocEntry(1234, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"),
            dumpBucket(bucketId));
}

void
ProcessAllHandlerTest::testStat()
{
    document::BucketId bucketId(16, 4);
    ProcessAllHandler handler(getEnv(), getPersistenceProvider());

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i), 0);
    }

    api::StatBucketCommand statBucket(bucketId,
                                      "testdoctype1.headerval % 2 == 0");
        spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
    MessageTracker::UP tracker = handler.handleStatBucket(statBucket, context);

    CPPUNIT_ASSERT(tracker->getReply().get());
    api::StatBucketReply& reply =
        dynamic_cast<api::StatBucketReply&>(*tracker->getReply().get());
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::OK, reply.getResult().getResult());

    vespalib::string expected =
        "Persistence bucket BucketId(0x4000000000000004), partition 0\n"
        "  Timestamp: 100, Doc(id:mail:testdoctype1:n=4:3619.html), gid(0x0400000092bb8d298934253a), size: 169\n"
        "  Timestamp: 102, Doc(id:mail:testdoctype1:n=4:62608.html), gid(0x04000000ce878d2488413bc4), size: 147\n"
        "  Timestamp: 104, Doc(id:mail:testdoctype1:n=4:56061.html), gid(0x040000002b8f80f0160f6c5c), size: 124\n"
        "  Timestamp: 106, Doc(id:mail:testdoctype1:n=4:49514.html), gid(0x04000000d45ca9abb47567f0), size: 101\n"
        "  Timestamp: 108, Doc(id:mail:testdoctype1:n=4:42967.html), gid(0x04000000f19ece1668e6de48), size: 206\n";


    CPPUNIT_ASSERT_EQUAL(expected, reply.getResults());
}

void
ProcessAllHandlerTest::testStatWithRemove()
{
    document::BucketId bucketId(16, 4);
    ProcessAllHandler handler(getEnv(), getPersistenceProvider());

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i), 0);
        doRemove(bucketId,
                 doc->getId(),
                 spi::Timestamp(200 + i),
                 true);
    }

    api::StatBucketCommand statBucket(bucketId, "true");
    spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
    MessageTracker::UP tracker = handler.handleStatBucket(statBucket, context);

    CPPUNIT_ASSERT(tracker->getReply().get());
    api::StatBucketReply& reply =
        dynamic_cast<api::StatBucketReply&>(*tracker->getReply().get());
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::OK, reply.getResult().getResult());

    vespalib::string expected =
        "Persistence bucket BucketId(0x4000000000000004), partition 0\n"
        "  Timestamp: 100, Doc(id:mail:testdoctype1:n=4:3619.html), gid(0x0400000092bb8d298934253a), size: 169\n"
        "  Timestamp: 101, Doc(id:mail:testdoctype1:n=4:33113.html), gid(0x04000000b121a632741db368), size: 95\n"
        "  Timestamp: 102, Doc(id:mail:testdoctype1:n=4:62608.html), gid(0x04000000ce878d2488413bc4), size: 147\n"
        "  Timestamp: 103, Doc(id:mail:testdoctype1:n=4:26566.html), gid(0x04000000177f8240bdd2bef0), size: 200\n"
        "  Timestamp: 104, Doc(id:mail:testdoctype1:n=4:56061.html), gid(0x040000002b8f80f0160f6c5c), size: 124\n"
        "  Timestamp: 105, Doc(id:mail:testdoctype1:n=4:20019.html), gid(0x040000001550c67f28ea7b03), size: 177\n"
        "  Timestamp: 106, Doc(id:mail:testdoctype1:n=4:49514.html), gid(0x04000000d45ca9abb47567f0), size: 101\n"
        "  Timestamp: 107, Doc(id:mail:testdoctype1:n=4:13472.html), gid(0x040000005d01f3fd960f8098), size: 154\n"
        "  Timestamp: 108, Doc(id:mail:testdoctype1:n=4:42967.html), gid(0x04000000f19ece1668e6de48), size: 206\n"
        "  Timestamp: 109, Doc(id:mail:testdoctype1:n=4:6925.html), gid(0x04000000667c0b3cada830be), size: 130\n"
        "  Timestamp: 200, id:mail:testdoctype1:n=4:3619.html, gid(0x0400000092bb8d298934253a) (remove)\n"
        "  Timestamp: 201, id:mail:testdoctype1:n=4:33113.html, gid(0x04000000b121a632741db368) (remove)\n"
        "  Timestamp: 202, id:mail:testdoctype1:n=4:62608.html, gid(0x04000000ce878d2488413bc4) (remove)\n"
        "  Timestamp: 203, id:mail:testdoctype1:n=4:26566.html, gid(0x04000000177f8240bdd2bef0) (remove)\n"
        "  Timestamp: 204, id:mail:testdoctype1:n=4:56061.html, gid(0x040000002b8f80f0160f6c5c) (remove)\n"
        "  Timestamp: 205, id:mail:testdoctype1:n=4:20019.html, gid(0x040000001550c67f28ea7b03) (remove)\n"
        "  Timestamp: 206, id:mail:testdoctype1:n=4:49514.html, gid(0x04000000d45ca9abb47567f0) (remove)\n"
        "  Timestamp: 207, id:mail:testdoctype1:n=4:13472.html, gid(0x040000005d01f3fd960f8098) (remove)\n"
        "  Timestamp: 208, id:mail:testdoctype1:n=4:42967.html, gid(0x04000000f19ece1668e6de48) (remove)\n"
        "  Timestamp: 209, id:mail:testdoctype1:n=4:6925.html, gid(0x04000000667c0b3cada830be) (remove)\n";

    CPPUNIT_ASSERT_EQUAL(expected, reply.getResults());
}


void
ProcessAllHandlerTest::testStatWholeBucket()
{
    document::BucketId bucketId(16, 4);
    ProcessAllHandler handler(getEnv(), getPersistenceProvider());

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i), 0);
    }

    api::StatBucketCommand statBucket(bucketId, "true");
    spi::Context context(documentapi::LoadType::DEFAULT, 0, 0);
    MessageTracker::UP tracker = handler.handleStatBucket(statBucket, context);

    CPPUNIT_ASSERT(tracker->getReply().get());
    api::StatBucketReply& reply =
        dynamic_cast<api::StatBucketReply&>(*tracker->getReply().get());
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::OK, reply.getResult().getResult());

    vespalib::string expected =
        "Persistence bucket BucketId(0x4000000000000004), partition 0\n"
        "  Timestamp: 100, Doc(id:mail:testdoctype1:n=4:3619.html), gid(0x0400000092bb8d298934253a), size: 169\n"
        "  Timestamp: 101, Doc(id:mail:testdoctype1:n=4:33113.html), gid(0x04000000b121a632741db368), size: 95\n"
        "  Timestamp: 102, Doc(id:mail:testdoctype1:n=4:62608.html), gid(0x04000000ce878d2488413bc4), size: 147\n"
        "  Timestamp: 103, Doc(id:mail:testdoctype1:n=4:26566.html), gid(0x04000000177f8240bdd2bef0), size: 200\n"
        "  Timestamp: 104, Doc(id:mail:testdoctype1:n=4:56061.html), gid(0x040000002b8f80f0160f6c5c), size: 124\n"
        "  Timestamp: 105, Doc(id:mail:testdoctype1:n=4:20019.html), gid(0x040000001550c67f28ea7b03), size: 177\n"
        "  Timestamp: 106, Doc(id:mail:testdoctype1:n=4:49514.html), gid(0x04000000d45ca9abb47567f0), size: 101\n"
        "  Timestamp: 107, Doc(id:mail:testdoctype1:n=4:13472.html), gid(0x040000005d01f3fd960f8098), size: 154\n"
        "  Timestamp: 108, Doc(id:mail:testdoctype1:n=4:42967.html), gid(0x04000000f19ece1668e6de48), size: 206\n"
        "  Timestamp: 109, Doc(id:mail:testdoctype1:n=4:6925.html), gid(0x04000000667c0b3cada830be), size: 130\n";

    CPPUNIT_ASSERT_EQUAL(expected, reply.getResults());
}

}
