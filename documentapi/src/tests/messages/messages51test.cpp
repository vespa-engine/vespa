// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messages51test.h"
#include <vespa/documentapi/documentapi.h>
#include <vespa/document/update/fieldpathupdates.h>

using document::DataType;
using document::DocumentTypeRepo;

///////////////////////////////////////////////////////////////////////////////
//
// Setup
//
///////////////////////////////////////////////////////////////////////////////

Messages51Test::Messages51Test()
{
    // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
    // version 5.0. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.
    putTest(DocumentProtocol::MESSAGE_CREATEVISITOR, TEST_METHOD(Messages51Test::testCreateVisitorMessage));
    putTest(DocumentProtocol::MESSAGE_GETDOCUMENT, TEST_METHOD(Messages51Test::testGetDocumentMessage));
    putTest(DocumentProtocol::REPLY_DOCUMENTIGNORED, TEST_METHOD(Messages51Test::testDocumentIgnoredReply));
}


///////////////////////////////////////////////////////////////////////////////
//
// Tests
//
///////////////////////////////////////////////////////////////////////////////

static const int MESSAGE_BASE_LENGTH = 5;

bool
Messages51Test::testCreateVisitorMessage() {
    CreateVisitorMessage tmp("SomeLibrary", "myvisitor", "newyork", "london");
    tmp.setDocumentSelection("true and false or true");
    tmp.getParameters().set("myvar", "somevalue");
    tmp.getParameters().set("anothervar", uint64_t(34));
    tmp.getBuckets().push_back(document::BucketId(16, 1234));
    tmp.setVisitRemoves(true);
    tmp.setFieldSet("foo bar");
    tmp.setVisitorOrdering(document::OrderingSpecification::DESCENDING);
    tmp.setMaxBucketsPerVisitor(2);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)178, serialize("CreateVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("CreateVisitorMessage", DocumentProtocol::MESSAGE_CREATEVISITOR, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            CreateVisitorMessage &ref = static_cast<CreateVisitorMessage&>(*obj);

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
        }
    }
    return true;
}

bool
Messages51Test::testGetDocumentMessage()
{
    GetDocumentMessage tmp(document::DocumentId("doc:scheme:"), "foo bar");

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)27, serialize("GetDocumentMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetDocumentMessage", DocumentProtocol::MESSAGE_GETDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            GetDocumentMessage &ref = static_cast<GetDocumentMessage&>(*obj);
            EXPECT_EQUAL(string("doc:scheme:"), ref.getDocumentId().toString());
            EXPECT_EQUAL(string("foo bar"), ref.getFieldSet());
        }
    }
    return true;
}

bool
Messages51Test::testDocumentIgnoredReply()
{
    DocumentIgnoredReply tmp;
    serialize("DocumentIgnoredReply", tmp);
    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj(
                deserialize("DocumentIgnoredReply",
                            DocumentProtocol::REPLY_DOCUMENTIGNORED, lang));
        EXPECT_TRUE(obj.get() != NULL);
    }
    return true;
}
