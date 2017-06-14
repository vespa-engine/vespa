// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/storagelinktest.h>
#include <iostream>
#include <string>
#include <vespa/storageapi/message/stat.h>

namespace storage {

CPPUNIT_TEST_SUITE_REGISTRATION(StorageLinkTest);

StorageLinkTest::StorageLinkTest()
    : _threadPool(1024),
      _feeder(),
      _middle(0),
      _replier(0) {}

void StorageLinkTest::setUp() {
    _feeder.reset(new DummyStorageLink());
    _middle = new DummyStorageLink();
    _replier = new DummyStorageLink();
    _feeder->push_back(StorageLink::UP(_middle));
    _feeder->push_back(StorageLink::UP(_replier));
    _replier->setAutoreply(true);
}

void StorageLinkTest::testPrinting() {
    std::ostringstream actual;
    actual << *_feeder;
    std::string expected =
"StorageChain(3)\n"
"  DummyStorageLink(autoreply = off, dispatch = off, 0 commands, 0 replies)\n"
"  DummyStorageLink(autoreply = off, dispatch = off, 0 commands, 0 replies)\n"
"  DummyStorageLink(autoreply = on, dispatch = off, 0 commands, 0 replies)";

    CPPUNIT_ASSERT_EQUAL(expected, actual.str());
}

void StorageLinkTest::testNotImplemented() {
    _feeder->open();
      // Test that a message that nobody handles fails with NOT_IMPLEMENTED
    _replier->setIgnore(true);
    _feeder->sendDown(api::StorageCommand::SP(
                new api::StatBucketCommand(document::BucketId(0), "")));
    _feeder->close();
    _feeder->flush();
    CPPUNIT_ASSERT_EQUAL((size_t) 1, _feeder->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(
            dynamic_cast<api::StatBucketReply&>(
                *_feeder->getReply(0)).getResult(),
            api::ReturnCode(api::ReturnCode::NOT_IMPLEMENTED, "Statbucket"));
    _feeder->reset();
    _replier->setIgnore(false);
}

} // storage
