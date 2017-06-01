// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cppunit/extensions/HelperMacros.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/fastos/thread.h>

namespace storage {

struct StorageLinkTest : public CppUnit::TestFixture {
    FastOS_ThreadPool _threadPool;
    std::unique_ptr<DummyStorageLink> _feeder;
    DummyStorageLink* _middle;
    DummyStorageLink* _replier;

    StorageLinkTest();

    void setUp() override;

    void testPrinting();
    void testNotImplemented();

    static bool callOnUp(StorageLink& link, const api::StorageMessage::SP& msg) {
        return link.onUp(msg);
    }
    static bool callOnDown(StorageLink& link, const api::StorageMessage::SP& msg) {
        return link.onDown(msg);
    }
    static void callOnFlush(StorageLink& link, bool downwards) {
        link.onFlush(downwards);
    }

    CPPUNIT_TEST_SUITE(StorageLinkTest);
    CPPUNIT_TEST(testPrinting);
    CPPUNIT_TEST(testNotImplemented);
    CPPUNIT_TEST_SUITE_END();
};

}
