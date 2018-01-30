// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messages60test.h"
#include <vespa/documentapi/documentapi.h>

static constexpr int MESSAGE_BASE_LENGTH = 5;

Messages60Test::Messages60Test() {
    // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
    // version 6.x. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.
    putTest(DocumentProtocol::MESSAGE_CREATEVISITOR, TEST_METHOD(Messages60Test::testCreateVisitorMessage));
    putTest(DocumentProtocol::MESSAGE_STATBUCKET, TEST_METHOD(Messages60Test::testStatBucketMessage));
    putTest(DocumentProtocol::MESSAGE_GETBUCKETLIST, TEST_METHOD(Messages60Test::testGetBucketListMessage));
}

// TODO code dupe with parent classes
bool Messages60Test::testCreateVisitorMessage() {
    CreateVisitorMessage tmp("SomeLibrary", "myvisitor", "newyork", "london");
    tmp.setDocumentSelection("true and false or true");
    tmp.getParameters().set("myvar", "somevalue");
    tmp.getParameters().set("anothervar", uint64_t(34));
    tmp.getBuckets().push_back(document::BucketId(16, 1234));
    tmp.setVisitRemoves(true);
    tmp.setFieldSet("foo bar");
    tmp.setVisitorOrdering(document::OrderingSpecification::DESCENDING);
    tmp.setMaxBucketsPerVisitor(2);
    tmp.setBucketSpace("bjarne");

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + serializedLength("bjarne") + 178, serialize("CreateVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("CreateVisitorMessage", DocumentProtocol::MESSAGE_CREATEVISITOR, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<CreateVisitorMessage&>(*obj);

            EXPECT_EQUAL(string("SomeLibrary"), ref.getLibraryName());
            EXPECT_EQUAL(string("myvisitor"), ref.getInstanceId());
            EXPECT_EQUAL(string("newyork"), ref.getControlDestination());
            EXPECT_EQUAL(string("london"), ref.getDataDestination());
            EXPECT_EQUAL(string("true and false or true"), ref.getDocumentSelection());
            EXPECT_EQUAL(string("foo bar"), ref.getFieldSet());
            EXPECT_EQUAL(uint32_t(8), ref.getMaximumPendingReplyCount());
            EXPECT_EQUAL(true, ref.visitRemoves());
            EXPECT_EQUAL(false, ref.visitHeadersOnly());
            EXPECT_EQUAL(false, ref.visitInconsistentBuckets());
            EXPECT_EQUAL(size_t(1), ref.getBuckets().size());
            EXPECT_EQUAL(document::BucketId(16, 1234), ref.getBuckets()[0]);
            EXPECT_EQUAL(string("somevalue"), ref.getParameters().get("myvar"));
            EXPECT_EQUAL(uint64_t(34), ref.getParameters().get("anothervar", uint64_t(1)));
            EXPECT_EQUAL(document::OrderingSpecification::DESCENDING, ref.getVisitorOrdering());
            EXPECT_EQUAL(uint32_t(2), ref.getMaxBucketsPerVisitor());
            EXPECT_EQUAL(string("bjarne"), ref.getBucketSpace());
        }
    }
    return true;
}

bool Messages60Test::testStatBucketMessage() {
    StatBucketMessage msg(document::BucketId(16, 123), "id.user=123");
    msg.setBucketSpace("andrei");

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 27u + serializedLength("andrei"), serialize("StatBucketMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("StatBucketMessage", DocumentProtocol::MESSAGE_STATBUCKET, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            StatBucketMessage &ref = static_cast<StatBucketMessage&>(*obj);
            EXPECT_EQUAL(document::BucketId(16, 123), ref.getBucketId());
            EXPECT_EQUAL("id.user=123", ref.getDocumentSelection());
            EXPECT_EQUAL("andrei", ref.getBucketSpace());
        }
    }
    return true;
}

bool Messages60Test::testGetBucketListMessage() {
    GetBucketListMessage msg(document::BucketId(16, 123));
    msg.setLoadType(_loadTypes["foo"]);
    msg.setBucketSpace("beartato");
    EXPECT_EQUAL(string("foo"), msg.getLoadType().getName());
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 12u + serializedLength("beartato"), serialize("GetBucketListMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketListMessage", DocumentProtocol::MESSAGE_GETBUCKETLIST, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            GetBucketListMessage &ref = static_cast<GetBucketListMessage&>(*obj);
            EXPECT_EQUAL(string("foo"), ref.getLoadType().getName());
            EXPECT_EQUAL(document::BucketId(16, 123), ref.getBucketId());
            EXPECT_EQUAL("beartato", ref.getBucketSpace());
        }
    }
    return true;
}