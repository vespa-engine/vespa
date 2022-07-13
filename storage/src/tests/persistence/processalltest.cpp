// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/storage/persistence/processallhandler.h>
#include <vespa/storage/persistence/asynchandler.h>
#include <vespa/storage/persistence/messages.h>
#include <tests/persistence/persistencetestutils.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>

using document::test::makeDocumentBucket;
using document::DocumentId;
using namespace ::testing;

namespace storage {

struct ProcessAllHandlerTest : public PersistenceTestUtils {
    document::BucketIdFactory _bucketIdFactory;
};

TEST_F(ProcessAllHandlerTest, change_of_repos_is_reflected) {
    EXPECT_EQ(2u, getComponent().getGeneration());
    auto old = getComponent().getTypeRepo()->documentTypeRepo;
    auto old2 = &getEnv().getDocumentTypeRepo();
    EXPECT_EQ(old.get(), old2);

    auto newDocRepo = std::make_shared<document::DocumentTypeRepo>(*old->getDocumentType("testdoctype1"));
    getComponent().setDocumentTypeRepo(newDocRepo);

    EXPECT_EQ(3u, getComponent().getGeneration());
    EXPECT_EQ(newDocRepo.get(), getComponent().getTypeRepo()->documentTypeRepo.get());
    EXPECT_EQ(newDocRepo.get(), &getEnv().getDocumentTypeRepo());
}

TEST_F(ProcessAllHandlerTest, legacy_remove_location) {
    document::BucketId bucketId(16, 4);
    doPut(4, spi::Timestamp(1234));
    doPut(4, spi::Timestamp(2345));

    document::Bucket bucket = makeDocumentBucket(bucketId);
    auto cmd = std::make_shared<api::RemoveLocationCommand>("id.user == 4", bucket);
    AsyncHandler handler(getEnv(), getPersistenceProvider(), _bucketOwnershipNotifier, *_sequenceTaskExecutor, _bucketIdFactory);
    auto tracker = handler.handleRemoveLocation(*cmd, createTracker(cmd, bucket));

    std::shared_ptr<api::StorageMessage> msg;
    ASSERT_TRUE(_replySender.queue.getNext(msg, 60s));

    EXPECT_EQ("DocEntry(1234, 1, id:mail:testdoctype1:n=4:3619.html)\n"
              "DocEntry(2345, 1, id:mail:testdoctype1:n=4:4008.html)\n",
              dumpBucket(bucketId));

    auto reply = std::dynamic_pointer_cast<api::RemoveLocationReply>(msg);
    ASSERT_TRUE(reply);
    EXPECT_EQ(2u, reply->documents_removed());
}

TEST_F(ProcessAllHandlerTest, legacy_remove_location_document_subset) {
    document::BucketId bucketId(16, 4);
    AsyncHandler handler(getEnv(), getPersistenceProvider(), _bucketOwnershipNotifier, *_sequenceTaskExecutor, _bucketIdFactory);

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i));
    }

    document::Bucket bucket = makeDocumentBucket(bucketId);
    auto cmd = std::make_shared<api::RemoveLocationCommand>("testdoctype1.headerval % 2 == 0", bucket);
    auto tracker = handler.handleRemoveLocation(*cmd, createTracker(cmd, bucket));

    std::shared_ptr<api::StorageMessage> msg;
    ASSERT_TRUE(_replySender.queue.getNext(msg, 60s));

    EXPECT_EQ("DocEntry(100, 1, id:mail:testdoctype1:n=4:3619.html)\n"
              "DocEntry(101, 0, Doc(id:mail:testdoctype1:n=4:33113.html))\n"
              "DocEntry(102, 1, id:mail:testdoctype1:n=4:62608.html)\n"
              "DocEntry(103, 0, Doc(id:mail:testdoctype1:n=4:26566.html))\n"
              "DocEntry(104, 1, id:mail:testdoctype1:n=4:56061.html)\n"
              "DocEntry(105, 0, Doc(id:mail:testdoctype1:n=4:20019.html))\n"
              "DocEntry(106, 1, id:mail:testdoctype1:n=4:49514.html)\n"
              "DocEntry(107, 0, Doc(id:mail:testdoctype1:n=4:13472.html))\n"
              "DocEntry(108, 1, id:mail:testdoctype1:n=4:42967.html)\n"
              "DocEntry(109, 0, Doc(id:mail:testdoctype1:n=4:6925.html))\n",
              dumpBucket(bucketId));

    auto reply = std::dynamic_pointer_cast<api::RemoveLocationReply>(msg);
    ASSERT_TRUE(reply);
    EXPECT_EQ(5u, reply->documents_removed());
}

TEST_F(ProcessAllHandlerTest, remove_location_with_enumerate_only_returns_match_set_only) {
    document::BucketId bucketId(16, 4);
    AsyncHandler handler(getEnv(), getPersistenceProvider(), _bucketOwnershipNotifier,
                         *_sequenceTaskExecutor, _bucketIdFactory);

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i));
    }

    document::Bucket bucket = makeDocumentBucket(bucketId);
    auto cmd = std::make_shared<api::RemoveLocationCommand>("testdoctype1.headerval % 2 == 0", bucket);
    cmd->set_only_enumerate_docs(true);
    auto tracker = handler.handleRemoveLocation(*cmd, createTracker(cmd, bucket));
    // Enumeration is synchronous, so we get the reply in the _tracker_, not on the reply queue.
    ASSERT_TRUE(tracker->hasReply());
    auto* reply = dynamic_cast<api::RemoveLocationReply*>(&tracker->getReply());
    ASSERT_TRUE(reply);
    EXPECT_EQ(0u, reply->documents_removed());

    // No docs should be removed (remove flag is all zero)
    EXPECT_EQ("DocEntry(100, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"
              "DocEntry(101, 0, Doc(id:mail:testdoctype1:n=4:33113.html))\n"
              "DocEntry(102, 0, Doc(id:mail:testdoctype1:n=4:62608.html))\n"
              "DocEntry(103, 0, Doc(id:mail:testdoctype1:n=4:26566.html))\n"
              "DocEntry(104, 0, Doc(id:mail:testdoctype1:n=4:56061.html))\n"
              "DocEntry(105, 0, Doc(id:mail:testdoctype1:n=4:20019.html))\n"
              "DocEntry(106, 0, Doc(id:mail:testdoctype1:n=4:49514.html))\n"
              "DocEntry(107, 0, Doc(id:mail:testdoctype1:n=4:13472.html))\n"
              "DocEntry(108, 0, Doc(id:mail:testdoctype1:n=4:42967.html))\n"
              "DocEntry(109, 0, Doc(id:mail:testdoctype1:n=4:6925.html))\n",
              dumpBucket(bucketId));

    std::vector<spi::IdAndTimestamp> expected = {
        {DocumentId("id:mail:testdoctype1:n=4:3619.html"),  spi::Timestamp(100)},
        {DocumentId("id:mail:testdoctype1:n=4:62608.html"), spi::Timestamp(102)},
        {DocumentId("id:mail:testdoctype1:n=4:56061.html"), spi::Timestamp(104)},
        {DocumentId("id:mail:testdoctype1:n=4:49514.html"), spi::Timestamp(106)},
        {DocumentId("id:mail:testdoctype1:n=4:42967.html"), spi::Timestamp(108)},
    };
    EXPECT_EQ(reply->selection_matches(), expected);
}

TEST_F(ProcessAllHandlerTest, remove_location_with_remove_set_only_removes_listed_docs) {
    document::BucketId bucketId(16, 4);
    AsyncHandler handler(getEnv(), getPersistenceProvider(), _bucketOwnershipNotifier,
                         *_sequenceTaskExecutor, _bucketIdFactory);

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i));
    }

    document::Bucket bucket = makeDocumentBucket(bucketId);
    // Use a selection that, if naively used, removes everything.
    auto cmd = std::make_shared<api::RemoveLocationCommand>("true", bucket);
    std::vector<spi::IdAndTimestamp> to_remove = {
        {DocumentId("id:mail:testdoctype1:n=4:62608.html"), spi::Timestamp(102)},
        {DocumentId("id:mail:testdoctype1:n=4:49514.html"), spi::Timestamp(106)},
        {DocumentId("id:mail:testdoctype1:n=4:42967.html"), spi::Timestamp(108)},
    };
    cmd->set_explicit_remove_set(std::move(to_remove));
    auto tracker = handler.handleRemoveLocation(*cmd, createTracker(cmd, bucket));
    // Actually removing the documents is asynchronous, so the response will be on the queue.
    std::shared_ptr<api::StorageMessage> msg;
    ASSERT_TRUE(_replySender.queue.getNext(msg, 60s));

    // Remove flag toggled for the entries provided in the command
    EXPECT_EQ("DocEntry(100, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n"
              "DocEntry(101, 0, Doc(id:mail:testdoctype1:n=4:33113.html))\n"
              "DocEntry(102, 1, id:mail:testdoctype1:n=4:62608.html)\n"
              "DocEntry(103, 0, Doc(id:mail:testdoctype1:n=4:26566.html))\n"
              "DocEntry(104, 0, Doc(id:mail:testdoctype1:n=4:56061.html))\n"
              "DocEntry(105, 0, Doc(id:mail:testdoctype1:n=4:20019.html))\n"
              "DocEntry(106, 1, id:mail:testdoctype1:n=4:49514.html)\n"
              "DocEntry(107, 0, Doc(id:mail:testdoctype1:n=4:13472.html))\n"
              "DocEntry(108, 1, id:mail:testdoctype1:n=4:42967.html)\n"
              "DocEntry(109, 0, Doc(id:mail:testdoctype1:n=4:6925.html))\n",
              dumpBucket(bucketId));

    auto reply = std::dynamic_pointer_cast<api::RemoveLocationReply>(msg);
    ASSERT_TRUE(reply);
    EXPECT_EQ(3u, reply->documents_removed());
}

TEST_F(ProcessAllHandlerTest, remove_location_throws_exception_on_unknown_doc_type) {
    document::BucketId bucketId(16, 4);
    doPut(4, spi::Timestamp(1234));

    document::Bucket bucket = makeDocumentBucket(bucketId);
    auto cmd = std::make_shared<api::RemoveLocationCommand>("unknowndoctype.headerval % 2 == 0", bucket);

    AsyncHandler handler(getEnv(), getPersistenceProvider(), _bucketOwnershipNotifier, *_sequenceTaskExecutor, _bucketIdFactory);
    ASSERT_THROW(handler.handleRemoveLocation(*cmd, createTracker(cmd, bucket)), std::exception);

    EXPECT_EQ("DocEntry(1234, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n",
              dumpBucket(bucketId));
}

TEST_F(ProcessAllHandlerTest, remove_location_throws_exception_on_bogus_selection) {
    document::BucketId bucketId(16, 4);
    doPut(4, spi::Timestamp(1234));

    document::Bucket bucket = makeDocumentBucket(bucketId);
    auto cmd = std::make_shared<api::RemoveLocationCommand>("id.bogus != badgers", bucket);

    AsyncHandler handler(getEnv(), getPersistenceProvider(), _bucketOwnershipNotifier, *_sequenceTaskExecutor, _bucketIdFactory);
    ASSERT_THROW(handler.handleRemoveLocation(*cmd, createTracker(cmd, bucket)), std::exception);

    EXPECT_EQ("DocEntry(1234, 0, Doc(id:mail:testdoctype1:n=4:3619.html))\n",
              dumpBucket(bucketId));
}

TEST_F(ProcessAllHandlerTest, bucket_stat_request_returns_document_metadata_matching_selection) {
    document::BucketId bucketId(16, 4);
    ProcessAllHandler handler(getEnv(), getPersistenceProvider());

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i));
    }

    document::Bucket bucket = makeDocumentBucket(bucketId);
    auto cmd = std::make_shared<api::StatBucketCommand>(bucket, "testdoctype1.headerval % 2 == 0");
    MessageTracker::UP tracker = handler.handleStatBucket(*cmd, createTracker(cmd, bucket));

    ASSERT_TRUE(tracker->hasReply());
    auto& reply = dynamic_cast<api::StatBucketReply&>(tracker->getReply());
    EXPECT_EQ(api::ReturnCode::OK, reply.getResult().getResult());

    vespalib::string expected =
        "Persistence bucket BucketId(0x4000000000000004)\n"
        "  Timestamp: 100, Doc(id:mail:testdoctype1:n=4:3619.html), gid(0x0400000092bb8d298934253a), size: 163\n"
        "  Timestamp: 102, Doc(id:mail:testdoctype1:n=4:62608.html), gid(0x04000000ce878d2488413bc4), size: 141\n"
        "  Timestamp: 104, Doc(id:mail:testdoctype1:n=4:56061.html), gid(0x040000002b8f80f0160f6c5c), size: 118\n"
        "  Timestamp: 106, Doc(id:mail:testdoctype1:n=4:49514.html), gid(0x04000000d45ca9abb47567f0), size: 95\n"
        "  Timestamp: 108, Doc(id:mail:testdoctype1:n=4:42967.html), gid(0x04000000f19ece1668e6de48), size: 200\n";

    EXPECT_EQ(expected, reply.getResults());
}

TEST_F(ProcessAllHandlerTest, stat_bucket_request_can_returned_removed_entries) {
    document::BucketId bucketId(16, 4);
    ProcessAllHandler handler(getEnv(), getPersistenceProvider());

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i));
        doRemove(bucketId,
                 doc->getId(),
                 spi::Timestamp(200 + i),
                 true);
    }

    document::Bucket bucket = makeDocumentBucket(bucketId);
    auto cmd = std::make_shared<api::StatBucketCommand>(bucket, "true");
    MessageTracker::UP tracker = handler.handleStatBucket(*cmd, createTracker(cmd, bucket));

    ASSERT_TRUE(tracker->hasReply());
    auto& reply = dynamic_cast<api::StatBucketReply&>(tracker->getReply());
    EXPECT_EQ(api::ReturnCode::OK, reply.getResult().getResult());

    vespalib::string expected =
        "Persistence bucket BucketId(0x4000000000000004)\n"
        "  Timestamp: 100, Doc(id:mail:testdoctype1:n=4:3619.html), gid(0x0400000092bb8d298934253a), size: 163\n"
        "  Timestamp: 101, Doc(id:mail:testdoctype1:n=4:33113.html), gid(0x04000000b121a632741db368), size: 89\n"
        "  Timestamp: 102, Doc(id:mail:testdoctype1:n=4:62608.html), gid(0x04000000ce878d2488413bc4), size: 141\n"
        "  Timestamp: 103, Doc(id:mail:testdoctype1:n=4:26566.html), gid(0x04000000177f8240bdd2bef0), size: 194\n"
        "  Timestamp: 104, Doc(id:mail:testdoctype1:n=4:56061.html), gid(0x040000002b8f80f0160f6c5c), size: 118\n"
        "  Timestamp: 105, Doc(id:mail:testdoctype1:n=4:20019.html), gid(0x040000001550c67f28ea7b03), size: 171\n"
        "  Timestamp: 106, Doc(id:mail:testdoctype1:n=4:49514.html), gid(0x04000000d45ca9abb47567f0), size: 95\n"
        "  Timestamp: 107, Doc(id:mail:testdoctype1:n=4:13472.html), gid(0x040000005d01f3fd960f8098), size: 148\n"
        "  Timestamp: 108, Doc(id:mail:testdoctype1:n=4:42967.html), gid(0x04000000f19ece1668e6de48), size: 200\n"
        "  Timestamp: 109, Doc(id:mail:testdoctype1:n=4:6925.html), gid(0x04000000667c0b3cada830be), size: 124\n"
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

    EXPECT_EQ(expected, reply.getResults());
}

// TODO is this test neccessary? Seems to not test anything more than the above tests
TEST_F(ProcessAllHandlerTest, bucket_stat_request_can_return_all_put_entries_in_bucket) {
    document::BucketId bucketId(16, 4);
    ProcessAllHandler handler(getEnv(), getPersistenceProvider());

    document::TestDocMan docMan;
    for (int i = 0; i < 10; ++i) {
        document::Document::SP doc(docMan.createRandomDocumentAtLocation(4, 1234 + i));
        doc->setValue(doc->getField("headerval"), document::IntFieldValue(i));
        doPut(doc, bucketId, spi::Timestamp(100 + i));
    }

    document::Bucket bucket = makeDocumentBucket(bucketId);
    auto cmd = std::make_shared<api::StatBucketCommand>(bucket, "true");
    MessageTracker::UP tracker = handler.handleStatBucket(*cmd, createTracker(cmd, bucket));

    ASSERT_TRUE(tracker->hasReply());
    auto& reply = dynamic_cast<api::StatBucketReply&>(tracker->getReply());
    EXPECT_EQ(api::ReturnCode::OK, reply.getResult().getResult());

    vespalib::string expected =
        "Persistence bucket BucketId(0x4000000000000004)\n"
        "  Timestamp: 100, Doc(id:mail:testdoctype1:n=4:3619.html), gid(0x0400000092bb8d298934253a), size: 163\n"
        "  Timestamp: 101, Doc(id:mail:testdoctype1:n=4:33113.html), gid(0x04000000b121a632741db368), size: 89\n"
        "  Timestamp: 102, Doc(id:mail:testdoctype1:n=4:62608.html), gid(0x04000000ce878d2488413bc4), size: 141\n"
        "  Timestamp: 103, Doc(id:mail:testdoctype1:n=4:26566.html), gid(0x04000000177f8240bdd2bef0), size: 194\n"
        "  Timestamp: 104, Doc(id:mail:testdoctype1:n=4:56061.html), gid(0x040000002b8f80f0160f6c5c), size: 118\n"
        "  Timestamp: 105, Doc(id:mail:testdoctype1:n=4:20019.html), gid(0x040000001550c67f28ea7b03), size: 171\n"
        "  Timestamp: 106, Doc(id:mail:testdoctype1:n=4:49514.html), gid(0x04000000d45ca9abb47567f0), size: 95\n"
        "  Timestamp: 107, Doc(id:mail:testdoctype1:n=4:13472.html), gid(0x040000005d01f3fd960f8098), size: 148\n"
        "  Timestamp: 108, Doc(id:mail:testdoctype1:n=4:42967.html), gid(0x04000000f19ece1668e6de48), size: 200\n"
        "  Timestamp: 109, Doc(id:mail:testdoctype1:n=4:6925.html), gid(0x04000000667c0b3cada830be), size: 124\n";

    EXPECT_EQ(expected, reply.getResults());
}

}
