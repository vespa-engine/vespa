// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/make_document_bucket.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>
#include <string>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct StorageLinkTest : public Test {
    std::unique_ptr<DummyStorageLink> _feeder;
    DummyStorageLink* _middle;
    DummyStorageLink* _replier;

    StorageLinkTest();

    void SetUp() override;
};

StorageLinkTest::StorageLinkTest()
    : _feeder(),
      _middle(nullptr),
      _replier(nullptr)
{}

void StorageLinkTest::SetUp() {
    _feeder = std::make_unique<DummyStorageLink>();
    _middle = new DummyStorageLink();
    _replier = new DummyStorageLink();
    _feeder->push_back(StorageLink::UP(_middle));
    _feeder->push_back(StorageLink::UP(_replier));
    _replier->setAutoreply(true);
}

TEST_F(StorageLinkTest, printing) {
    std::ostringstream actual;
    actual << *_feeder;
    std::string expected =
"StorageChain(3)\n"
"  DummyStorageLink(autoreply = off, dispatch = off, 0 commands, 0 replies)\n"
"  DummyStorageLink(autoreply = off, dispatch = off, 0 commands, 0 replies)\n"
"  DummyStorageLink(autoreply = on, dispatch = off, 0 commands, 0 replies)";

    EXPECT_EQ(expected, actual.str());
}

TEST_F(StorageLinkTest, not_implemented) {
    _feeder->open();
      // Test that a message that nobody handles fails with NOT_IMPLEMENTED
    _replier->setIgnore(true);
    _feeder->sendDown(std::make_shared<api::StatBucketCommand>(makeDocumentBucket(document::BucketId(0)), ""));
    _feeder->close();
    _feeder->flush();
    ASSERT_EQ(1, _feeder->getNumReplies());
    EXPECT_EQ(
            dynamic_cast<api::StatBucketReply&>(*_feeder->getReply(0)).getResult(),
            api::ReturnCode(api::ReturnCode::NOT_IMPLEMENTED, "Statbucket"));
    _feeder->reset();
    _replier->setIgnore(false);
}

} // storage
