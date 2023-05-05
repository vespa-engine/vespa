// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include "messages60test.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/featureset.h>
#include <vespa/vespalib/test/insertion_operators.h>

using document::DataType;
using document::DocumentTypeRepo;
using vespalib::FeatureValues;

namespace {

std::vector<char> doc1_mf_data{'H', 'i'};
std::vector<char> doc2_mf_data{'T', 'h', 'e', 'r', 'e'};

}

template <typename T>
struct Unwrap {
    mbus::Routable::UP value;
    const T *ptr = nullptr;
    explicit Unwrap(mbus::Routable::UP value_in) : value(std::move(value_in)) {
        ptr = dynamic_cast<T*>(value.get());
        ASSERT_TRUE(ptr != nullptr);
    }
    const T *operator->() const noexcept { return ptr; }
};

///////////////////////////////////////////////////////////////////////////////
//
// Setup
//
///////////////////////////////////////////////////////////////////////////////

Messages60Test::Messages60Test()
{
    // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
    // version 5.0. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.
    putTest(DocumentProtocol::MESSAGE_CREATEVISITOR, TEST_METHOD(Messages60Test::testCreateVisitorMessage));
    putTest(DocumentProtocol::MESSAGE_DESTROYVISITOR, TEST_METHOD(Messages60Test::testDestroyVisitorMessage));
    putTest(DocumentProtocol::MESSAGE_DOCUMENTLIST, TEST_METHOD(Messages60Test::testDocumentListMessage));
    putTest(DocumentProtocol::MESSAGE_EMPTYBUCKETS, TEST_METHOD(Messages60Test::testEmptyBucketsMessage));
    putTest(DocumentProtocol::MESSAGE_GETBUCKETLIST, TEST_METHOD(Messages60Test::testGetBucketListMessage));
    putTest(DocumentProtocol::MESSAGE_GETBUCKETSTATE, TEST_METHOD(Messages60Test::testGetBucketStateMessage));
    putTest(DocumentProtocol::MESSAGE_GETDOCUMENT, TEST_METHOD(Messages60Test::testGetDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_MAPVISITOR, TEST_METHOD(Messages60Test::testMapVisitorMessage));
    putTest(DocumentProtocol::MESSAGE_PUTDOCUMENT, TEST_METHOD(Messages60Test::testPutDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_QUERYRESULT, TEST_METHOD(Messages60Test::testQueryResultMessage));
    putTest(DocumentProtocol::MESSAGE_REMOVEDOCUMENT, TEST_METHOD(Messages60Test::testRemoveDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_REMOVELOCATION, TEST_METHOD(Messages60Test::testRemoveLocationMessage));
    putTest(DocumentProtocol::MESSAGE_STATBUCKET, TEST_METHOD(Messages60Test::testStatBucketMessage));
    putTest(DocumentProtocol::MESSAGE_UPDATEDOCUMENT, TEST_METHOD(Messages60Test::testUpdateDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_VISITORINFO, TEST_METHOD(Messages60Test::testVisitorInfoMessage));

    putTest(DocumentProtocol::REPLY_CREATEVISITOR, TEST_METHOD(Messages60Test::testCreateVisitorReply));
    putTest(DocumentProtocol::REPLY_DESTROYVISITOR, TEST_METHOD(Messages60Test::testDestroyVisitorReply));
    putTest(DocumentProtocol::REPLY_DOCUMENTIGNORED, TEST_METHOD(Messages60Test::testDocumentIgnoredReply));
    putTest(DocumentProtocol::REPLY_DOCUMENTLIST, TEST_METHOD(Messages60Test::testDocumentListReply));
    putTest(DocumentProtocol::REPLY_EMPTYBUCKETS, TEST_METHOD(Messages60Test::testEmptyBucketsReply));
    putTest(DocumentProtocol::REPLY_GETBUCKETLIST, TEST_METHOD(Messages60Test::testGetBucketListReply));
    putTest(DocumentProtocol::REPLY_GETBUCKETSTATE, TEST_METHOD(Messages60Test::testGetBucketStateReply));
    putTest(DocumentProtocol::REPLY_GETDOCUMENT, TEST_METHOD(Messages60Test::testGetDocumentReply));
    putTest(DocumentProtocol::REPLY_MAPVISITOR, TEST_METHOD(Messages60Test::testMapVisitorReply));
    putTest(DocumentProtocol::REPLY_PUTDOCUMENT, TEST_METHOD(Messages60Test::testPutDocumentReply));
    putTest(DocumentProtocol::REPLY_QUERYRESULT, TEST_METHOD(Messages60Test::testQueryResultReply));
    putTest(DocumentProtocol::REPLY_REMOVEDOCUMENT, TEST_METHOD(Messages60Test::testRemoveDocumentReply));
    putTest(DocumentProtocol::REPLY_REMOVELOCATION, TEST_METHOD(Messages60Test::testRemoveLocationReply));
    putTest(DocumentProtocol::REPLY_STATBUCKET, TEST_METHOD(Messages60Test::testStatBucketReply));
    putTest(DocumentProtocol::REPLY_UPDATEDOCUMENT, TEST_METHOD(Messages60Test::testUpdateDocumentReply));
    putTest(DocumentProtocol::REPLY_VISITORINFO, TEST_METHOD(Messages60Test::testVisitorInfoReply));
    putTest(DocumentProtocol::REPLY_WRONGDISTRIBUTION, TEST_METHOD(Messages60Test::testWrongDistributionReply));
}



///////////////////////////////////////////////////////////////////////////////
//
// Tests
//
///////////////////////////////////////////////////////////////////////////////

static const int MESSAGE_BASE_LENGTH = 5;

namespace {

document::Document::SP
createDoc(const DocumentTypeRepo &repo, const string &type_name, const string &id)
{
    return std::make_shared<document::Document>(repo,
                    *repo.getDocumentType(type_name),
                    document::DocumentId(id));
}

}  // namespace

bool
Messages60Test::testGetBucketListMessage()
{
    GetBucketListMessage msg(document::BucketId(16, 123));
    msg.setBucketSpace("beartato");
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 12u + serializedLength("beartato"), serialize("GetBucketListMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketListMessage", DocumentProtocol::MESSAGE_GETBUCKETLIST, lang);
        if (EXPECT_TRUE(obj)) {
            GetBucketListMessage &ref = static_cast<GetBucketListMessage&>(*obj);
            EXPECT_EQUAL(document::BucketId(16, 123), ref.getBucketId());
            EXPECT_EQUAL("beartato", ref.getBucketSpace());
        }
    }
    return true;
}

bool
Messages60Test::testEmptyBucketsMessage()
{
    std::vector<document::BucketId> bids;
    for (size_t i=0; i < 13; ++i) {
        bids.push_back(document::BucketId(16, i));
    }

    EmptyBucketsMessage msg(bids);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 112u, serialize("EmptyBucketsMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("EmptyBucketsMessage", DocumentProtocol::MESSAGE_EMPTYBUCKETS, lang);
        if (EXPECT_TRUE(obj)) {
            EmptyBucketsMessage &ref = static_cast<EmptyBucketsMessage&>(*obj);
            for (size_t i=0; i < 13; ++i) {
                EXPECT_EQUAL(document::BucketId(16, i), ref.getBucketIds()[i]);
            }
        }
    }
    return true;
}


bool
Messages60Test::testStatBucketMessage()
{
    StatBucketMessage msg(document::BucketId(16, 123), "id.user=123");
    msg.setBucketSpace("andrei");

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 27u + serializedLength("andrei"), serialize("StatBucketMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("StatBucketMessage", DocumentProtocol::MESSAGE_STATBUCKET, lang);
        if (EXPECT_TRUE(obj)) {
            StatBucketMessage &ref = static_cast<StatBucketMessage&>(*obj);
            EXPECT_EQUAL(document::BucketId(16, 123), ref.getBucketId());
            EXPECT_EQUAL("id.user=123", ref.getDocumentSelection());
            EXPECT_EQUAL("andrei", ref.getBucketSpace());
        }
    }
    return true;
}

bool
Messages60Test::testCreateVisitorMessage()
{
    CreateVisitorMessage tmp("SomeLibrary", "myvisitor", "newyork", "london");
    tmp.setDocumentSelection("true and false or true");
    tmp.getParameters().set("myvar", "somevalue");
    tmp.getParameters().set("anothervar", uint64_t(34));
    tmp.getBuckets().push_back(document::BucketId(16, 1234));
    tmp.setVisitRemoves(true);
    tmp.setFieldSet("foo bar");
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
            EXPECT_EQUAL(false, ref.visitInconsistentBuckets());
            EXPECT_EQUAL(size_t(1), ref.getBuckets().size());
            EXPECT_EQUAL(document::BucketId(16, 1234), ref.getBuckets()[0]);
            EXPECT_EQUAL(string("somevalue"), ref.getParameters().get("myvar"));
            EXPECT_EQUAL(uint64_t(34), ref.getParameters().get("anothervar", uint64_t(1)));
            EXPECT_EQUAL(uint32_t(2), ref.getMaxBucketsPerVisitor());
            EXPECT_EQUAL(string("bjarne"), ref.getBucketSpace());
        }
    }
    return true;
}

bool
Messages60Test::testDestroyVisitorMessage()
{
    DestroyVisitorMessage tmp("myvisitor");

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)17, serialize("DestroyVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("DestroyVisitorMessage", DocumentProtocol::MESSAGE_DESTROYVISITOR, lang);
        if (EXPECT_TRUE(obj)) {
            DestroyVisitorMessage &ref = static_cast<DestroyVisitorMessage&>(*obj);
            EXPECT_EQUAL(string("myvisitor"), ref.getInstanceId());
        }
    }
    return true;
}

bool
Messages60Test::testDocumentListMessage()
{
    document::Document::SP doc =
        createDoc(getTypeRepo(), "testdoc", "id:scheme:testdoc:n=1234:1");
    DocumentListMessage::Entry entry(1234, doc, false);

    DocumentListMessage tmp(document::BucketId(16, 1234));
    tmp.getDocuments().push_back(entry);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 69ul, serialize("DocumentListMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("DocumentListMessage", DocumentProtocol::MESSAGE_DOCUMENTLIST, lang);
        if (EXPECT_TRUE(obj)) {
            DocumentListMessage &ref = static_cast<DocumentListMessage&>(*obj);

            EXPECT_EQUAL("id:scheme:testdoc:n=1234:1", ref.getDocuments()[0].getDocument()->getId().toString());
            EXPECT_EQUAL(1234, ref.getDocuments()[0].getTimestamp());
            EXPECT_TRUE(!ref.getDocuments()[0].isRemoveEntry());
        }
    }
    return true;
}


bool
Messages60Test::testRemoveLocationMessage()
{
    {
        document::BucketIdFactory factory;
        document::select::Parser parser(getTypeRepo(), factory);
        RemoveLocationMessage msg(factory, parser, "id.group == \"mygroup\"");

        EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 29u, serialize("RemoveLocationMessage", msg));
        for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
            mbus::Routable::UP obj = deserialize("RemoveLocationMessage", DocumentProtocol::MESSAGE_REMOVELOCATION, lang);
            if (EXPECT_TRUE(obj)) {
                RemoveLocationMessage &ref = static_cast<RemoveLocationMessage&>(*obj);
                EXPECT_EQUAL(string("id.group == \"mygroup\""), ref.getDocumentSelection());
                // FIXME add to wire format, currently hardcoded.
                EXPECT_EQUAL(string(document::FixedBucketSpaces::default_space_name()), ref.getBucketSpace());
            }
        }
    }

    return true;
}



bool
Messages60Test::testGetDocumentMessage()
{
    GetDocumentMessage tmp(document::DocumentId("id:ns:testdoc::"), "foo bar");

    EXPECT_EQUAL(280u, sizeof(GetDocumentMessage));
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)31, serialize("GetDocumentMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetDocumentMessage", DocumentProtocol::MESSAGE_GETDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            GetDocumentMessage &ref = static_cast<GetDocumentMessage&>(*obj);
            EXPECT_EQUAL(string("id:ns:testdoc::"), ref.getDocumentId().toString());
            EXPECT_EQUAL(string("foo bar"), ref.getFieldSet());
        }
    }
    return true;
}

bool
Messages60Test::testMapVisitorMessage()
{
    MapVisitorMessage tmp;
    tmp.getData().set("foo", 3);
    tmp.getData().set("bar", 5);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)32, serialize("MapVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("MapVisitorMessage", DocumentProtocol::MESSAGE_MAPVISITOR, lang);
        if (EXPECT_TRUE(obj)) {
            MapVisitorMessage &ref = static_cast<MapVisitorMessage&>(*obj);
            EXPECT_EQUAL(3, ref.getData().get("foo", 0));
            EXPECT_EQUAL(5, ref.getData().get("bar", 0));
        }
    }
    return true;
}

bool
Messages60Test::testCreateVisitorReply()
{
    CreateVisitorReply reply(DocumentProtocol::REPLY_CREATEVISITOR);
    reply.setLastBucket(document::BucketId(16, 123));
    vdslib::VisitorStatistics vs;
    vs.setBucketsVisited(3);
    vs.setDocumentsVisited(1000);
    vs.setBytesVisited(1024000);
    vs.setDocumentsReturned(123);
    vs.setBytesReturned(512000);
    reply.setVisitorStatistics(vs);

    EXPECT_EQUAL(65u, serialize("CreateVisitorReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("CreateVisitorReply", DocumentProtocol::REPLY_CREATEVISITOR, lang);
        if (EXPECT_TRUE(obj)) {
            CreateVisitorReply &ref = static_cast<CreateVisitorReply&>(*obj);

            EXPECT_EQUAL(ref.getLastBucket(), document::BucketId(16, 123));
            EXPECT_EQUAL(ref.getVisitorStatistics().getBucketsVisited(), (uint32_t)3);
            EXPECT_EQUAL(ref.getVisitorStatistics().getDocumentsVisited(), (uint64_t)1000);
            EXPECT_EQUAL(ref.getVisitorStatistics().getBytesVisited(), (uint64_t)1024000);
            EXPECT_EQUAL(ref.getVisitorStatistics().getDocumentsReturned(), (uint64_t)123);
            EXPECT_EQUAL(ref.getVisitorStatistics().getBytesReturned(), (uint64_t)512000);
        }
    }
    return true;
}

bool
Messages60Test::testPutDocumentMessage()
{
    auto doc = createDoc(getTypeRepo(), "testdoc", "id:ns:testdoc::");
    PutDocumentMessage msg(doc);

    msg.setTimestamp(666);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQUAL(64u, sizeof(vespalib::string));
    EXPECT_EQUAL(sizeof(vespalib::string), sizeof(TestAndSetCondition));
    EXPECT_EQUAL(112u, sizeof(DocumentMessage));
    EXPECT_EQUAL(sizeof(TestAndSetCondition) + sizeof(DocumentMessage), sizeof(TestAndSetMessage));
    EXPECT_EQUAL(sizeof(TestAndSetMessage) + 32, sizeof(PutDocumentMessage));
    int size_of_create_if_non_existent_flag = 1;
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH +
                 45u +
                 serializedLength(msg.getCondition().getSelection()) +
                 size_of_create_if_non_existent_flag,
                 serialize("PutDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routableUp = deserialize("PutDocumentMessage", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang);
        if (EXPECT_TRUE(routableUp)) {
            auto & deserializedMsg = static_cast<PutDocumentMessage &>(*routableUp);

            EXPECT_EQUAL(msg.getDocument().getType().getName(), deserializedMsg.getDocument().getType().getName());
            EXPECT_EQUAL(msg.getDocument().getId().toString(), deserializedMsg.getDocument().getId().toString());
            EXPECT_EQUAL(msg.getTimestamp(), deserializedMsg.getTimestamp());
            EXPECT_EQUAL(72u, deserializedMsg.getApproxSize());
            EXPECT_EQUAL(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
            EXPECT_EQUAL(false, deserializedMsg.get_create_if_non_existent());
        }
    }

    //-------------------------------------------------------------------------

    PutDocumentMessage msg2(createDoc(getTypeRepo(), "testdoc", "id:ns:testdoc::"));
    msg2.set_create_if_non_existent(true);
    uint32_t expected_message_size = MESSAGE_BASE_LENGTH + 45u +
        serializedLength(msg2.getCondition().getSelection()) +
        size_of_create_if_non_existent_flag;
    auto trunc1 = [](mbus::Blob x) noexcept { return truncate(std::move(x), 1); };
    auto pad1 = [](mbus::Blob x) noexcept { return pad(std::move(x), 1); };
    EXPECT_EQUAL(expected_message_size, serialize("PutDocumentMessage-create", msg2));
    EXPECT_EQUAL(expected_message_size - 1, serialize("PutDocumentMessage-create-truncate", msg2, trunc1));
    EXPECT_EQUAL(expected_message_size + 1, serialize("PutDocumentMessage-create-pad", msg2, pad1));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto decoded = Unwrap<PutDocumentMessage>(deserialize("PutDocumentMessage-create", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang));
        auto decoded_trunc = Unwrap<PutDocumentMessage>(deserialize("PutDocumentMessage-create-truncate", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang));
        auto decoded_pad = Unwrap<PutDocumentMessage>(deserialize("PutDocumentMessage-create-pad", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang));
        EXPECT_EQUAL(true, decoded->get_create_if_non_existent());
        EXPECT_EQUAL(false, decoded_trunc->get_create_if_non_existent());
        EXPECT_EQUAL(true, decoded_pad->get_create_if_non_existent());
    }

    return true;
}

bool
Messages60Test::testGetBucketStateMessage()
{
    GetBucketStateMessage tmp;
    tmp.setBucketId(document::BucketId(16, 666));
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 12u, serialize("GetBucketStateMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketStateMessage", DocumentProtocol::MESSAGE_GETBUCKETSTATE, lang);
        if (EXPECT_TRUE(obj)) {
            GetBucketStateMessage &ref = static_cast<GetBucketStateMessage&>(*obj);

            EXPECT_EQUAL(16u, ref.getBucketId().getUsedBits());
            EXPECT_EQUAL(4611686018427388570ull, ref.getBucketId().getId());
        }
    }
    return true;
}

bool
Messages60Test::testPutDocumentReply()
{
    WriteDocumentReply reply(DocumentProtocol::REPLY_PUTDOCUMENT);
    reply.setHighestModificationTimestamp(30);

    EXPECT_EQUAL(13u, serialize("PutDocumentReply", reply));
    EXPECT_EQUAL(112u, sizeof(WriteDocumentReply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("PutDocumentReply", DocumentProtocol::REPLY_PUTDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            WriteDocumentReply &ref = static_cast<WriteDocumentReply&>(*obj);
            EXPECT_EQUAL(30u, ref.getHighestModificationTimestamp());
        }
    }
    return true;
}

bool
Messages60Test::testUpdateDocumentReply()
{
    UpdateDocumentReply reply;
    reply.setWasFound(false);
    reply.setHighestModificationTimestamp(30);

    EXPECT_EQUAL(14u, serialize("UpdateDocumentReply", reply));
    EXPECT_EQUAL(120u, sizeof(UpdateDocumentReply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("UpdateDocumentReply", DocumentProtocol::REPLY_UPDATEDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            UpdateDocumentReply &ref = static_cast<UpdateDocumentReply&>(*obj);
            EXPECT_EQUAL(30u, ref.getHighestModificationTimestamp());
            EXPECT_EQUAL(false, ref.wasFound());
        }
    }
    return true;
}

bool
Messages60Test::testRemoveDocumentMessage()
{
    RemoveDocumentMessage msg(document::DocumentId("id:ns:testdoc::"));

    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQUAL(sizeof(TestAndSetMessage) + 104, sizeof(RemoveDocumentMessage));
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + size_t(20) + serializedLength(msg.getCondition().getSelection()), serialize("RemoveDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routablePtr = deserialize("RemoveDocumentMessage", DocumentProtocol::MESSAGE_REMOVEDOCUMENT, lang);

        if (EXPECT_TRUE(routablePtr)) {
            auto & ref = static_cast<RemoveDocumentMessage &>(*routablePtr);
            EXPECT_EQUAL(string("id:ns:testdoc::"), ref.getDocumentId().toString());
            EXPECT_EQUAL(msg.getCondition().getSelection(), ref.getCondition().getSelection());
        }
    }
    return true;
}

bool
Messages60Test::testRemoveDocumentReply()
{
    RemoveDocumentReply reply;
    std::vector<uint64_t> ts;
    reply.setWasFound(false);
    reply.setHighestModificationTimestamp(30);
    EXPECT_EQUAL(120u, sizeof(RemoveDocumentReply));

    EXPECT_EQUAL(14u, serialize("RemoveDocumentReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("RemoveDocumentReply", DocumentProtocol::REPLY_REMOVEDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            RemoveDocumentReply &ref = static_cast<RemoveDocumentReply&>(*obj);
            EXPECT_EQUAL(30u, ref.getHighestModificationTimestamp());
            EXPECT_EQUAL(false, ref.wasFound());
        }
    }
    return true;
}

bool
Messages60Test::testUpdateDocumentMessage()
{
    const DocumentTypeRepo & repo = getTypeRepo();
    const document::DocumentType & docType = *repo.getDocumentType("testdoc");

    auto docUpdate = std::make_shared<document::DocumentUpdate>(repo, docType, document::DocumentId("id:ns:testdoc::"));

    docUpdate->addFieldPathUpdate(std::make_unique<document::RemoveFieldPathUpdate>("intfield", "testdoc.intfield > 0"));

    UpdateDocumentMessage msg(docUpdate);
    msg.setOldTimestamp(666u);
    msg.setNewTimestamp(777u);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQUAL(sizeof(TestAndSetMessage) + 32, sizeof(UpdateDocumentMessage));
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 93u + serializedLength(msg.getCondition().getSelection()), serialize("UpdateDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routableUp = deserialize("UpdateDocumentMessage", DocumentProtocol::MESSAGE_UPDATEDOCUMENT, lang);

        if (EXPECT_TRUE(routableUp)) {
            auto & deserializedMsg = static_cast<UpdateDocumentMessage &>(*routableUp);
            EXPECT_EQUAL(msg.getDocumentUpdate(), deserializedMsg.getDocumentUpdate());
            EXPECT_EQUAL(msg.getOldTimestamp(), deserializedMsg.getOldTimestamp());
            EXPECT_EQUAL(msg.getNewTimestamp(), deserializedMsg.getNewTimestamp());
            EXPECT_EQUAL(119u, deserializedMsg.getApproxSize());
            EXPECT_EQUAL(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
        }
    }
    return true;
}

bool
Messages60Test::testQueryResultMessage()
{
    QueryResultMessage srm;
    vdslib::SearchResult & sr(srm.getSearchResult());
    EXPECT_EQUAL(srm.getSequenceId(), 0u);
    EXPECT_EQUAL(sr.getHitCount(), 0u);
    EXPECT_EQUAL(sr.getAggregatorList().getSerializedSize(), 4u);
    EXPECT_EQUAL(sr.getSerializedSize(), 20u);
    EXPECT_EQUAL(srm.getApproxSize(), 28u);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + size_t(32), serialize("QueryResultMessage-1", srm));

    mbus::Routable::UP routable = deserialize("QueryResultMessage-1", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    if (!EXPECT_TRUE(routable)) {
        return false;
    }
    QueryResultMessage * dm = static_cast<QueryResultMessage *>(routable.get());
    vdslib::SearchResult * dr(&dm->getSearchResult());
    EXPECT_EQUAL(dm->getSequenceId(), size_t(0));
    EXPECT_EQUAL(dr->getHitCount(), size_t(0));

    sr.addHit(0, "doc1", 89);
    sr.addHit(1, "doc17", 109);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 63u, serialize("QueryResultMessage-2", srm));
    routable = deserialize("QueryResultMessage-2", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    if (!EXPECT_TRUE(routable)) {
        return false;
    }
    dm = static_cast<QueryResultMessage *>(routable.get());
    dr = &dm->getSearchResult();
    EXPECT_EQUAL(dr->getHitCount(), size_t(2));
    const char *docId;
    vdslib::SearchResult::RankType rank;
    dr->getHit(0, docId, rank);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    dr->getHit(1, docId, rank);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);

    sr.sort();

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 63u, serialize("QueryResultMessage-3", srm));
    routable = deserialize("QueryResultMessage-3", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    if (!EXPECT_TRUE(routable)) {
        return false;
    }
    dm = static_cast<QueryResultMessage *>(routable.get());
    dr = &dm->getSearchResult();
    EXPECT_EQUAL(dr->getHitCount(), size_t(2));
    dr->getHit(0, docId, rank);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);
    dr->getHit(1, docId, rank);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);

    QueryResultMessage srm2;
    vdslib::SearchResult & sr2(srm2.getSearchResult());
    sr2.addHit(0, "doc1", 89, "sortdata2", 9);
    sr2.addHit(1, "doc17", 109, "sortdata1", 9);
    sr2.addHit(2, "doc18", 90, "sortdata3", 9);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 116u, serialize("QueryResultMessage-4", srm2));
    routable = deserialize("QueryResultMessage-4", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    if (!EXPECT_TRUE(routable)) {
        return false;
    }
    dm = static_cast<QueryResultMessage *>(routable.get());
    dr = &dm->getSearchResult();
    EXPECT_EQUAL(dr->getHitCount(), size_t(3));
    dr->getHit(0, docId, rank);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    dr->getHit(1, docId, rank);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);
    dr->getHit(2, docId, rank);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(90));
    EXPECT_EQUAL(strcmp("doc18", docId), 0);

    sr2.sort();
    const void *buf;
    size_t sz;
    sr2.getHit(0, docId, rank);
    sr2.getSortBlob(0, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata1", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);
    sr2.getHit(1, docId, rank);
    sr2.getSortBlob(1, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata2", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    sr2.getHit(2, docId, rank);
    sr2.getSortBlob(2, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata3", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(90));
    EXPECT_EQUAL(strcmp("doc18", docId), 0);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 116u, serialize("QueryResultMessage-5", srm2));
    routable = deserialize("QueryResultMessage-5", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    if (!EXPECT_TRUE(routable)) {
        return false;
    }
    dm = static_cast<QueryResultMessage *>(routable.get());
    dr = &dm->getSearchResult();
    EXPECT_EQUAL(dr->getHitCount(), size_t(3));
    dr->getHit(0, docId, rank);
    dr->getSortBlob(0, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata1", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);
    dr->getHit(1, docId, rank);
    dr->getSortBlob(1, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata2", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    dr->getHit(2, docId, rank);
    dr->getSortBlob(2, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata3", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(90));
    EXPECT_EQUAL(strcmp("doc18", docId), 0);

    QueryResultMessage qrm3;
    auto& sr3(qrm3.getSearchResult());
    sr3.addHit(0, "doc1", 5);
    sr3.addHit(1, "doc2", 7);
    FeatureValues mf;
    mf.names.push_back("foo");
    mf.names.push_back("bar");
    mf.values.resize(4);
    mf.values[0].set_double(1.0);
    mf.values[1].set_data({ doc1_mf_data.data(), doc1_mf_data.size()});
    mf.values[2].set_double(12.0);
    mf.values[3].set_data({ doc2_mf_data.data(), doc2_mf_data.size()});
    sr3.set_match_features(FeatureValues(mf));
    sr3.sort();

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 125u, serialize("QueryResultMessage-6", qrm3));
    routable = deserialize("QueryResultMessage-6", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    if (!EXPECT_TRUE(routable)) {
        return false;
    }
    dm = static_cast<QueryResultMessage *>(routable.get());
    dr = &dm->getSearchResult();
    EXPECT_EQUAL(size_t(2), dr->getHitCount());
    dr->getHit(0, docId, rank);
    EXPECT_EQUAL(vdslib::SearchResult::RankType(7), rank);
    EXPECT_EQUAL(strcmp("doc2", docId), 0);
    dr->getHit(1, docId, rank);
    EXPECT_EQUAL(vdslib::SearchResult::RankType(5), rank);
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    auto mfv = dr->get_match_feature_values(0);
    EXPECT_EQUAL(2u, mfv.size());
    EXPECT_EQUAL(12.0, mfv[0].as_double());
    EXPECT_EQUAL("There", mfv[1].as_data().make_string());
    mfv = dr->get_match_feature_values(1);
    EXPECT_EQUAL(2u, mfv.size());
    EXPECT_EQUAL(1.0, mfv[0].as_double());
    EXPECT_EQUAL("Hi", mfv[1].as_data().make_string());
    const auto& mf_names = dr->get_match_features().names;
    EXPECT_EQUAL(2u, mf_names.size());
    EXPECT_EQUAL("foo", mf_names[0]);
    EXPECT_EQUAL("bar", mf_names[1]);
    return true;
}

bool
Messages60Test::testQueryResultReply()
{
    return tryVisitorReply("QueryResultReply", DocumentProtocol::REPLY_QUERYRESULT);
}

bool
Messages60Test::testVisitorInfoMessage()
{

    VisitorInfoMessage tmp;
    tmp.getFinishedBuckets().push_back(document::BucketId(16, 1));
    tmp.getFinishedBuckets().push_back(document::BucketId(16, 2));
    tmp.getFinishedBuckets().push_back(document::BucketId(16, 4));
    string utf8 = "error message: \u00e6\u00c6\u00f8\u00d8\u00e5\u00c5\u00f6\u00d6";
    tmp.setErrorMessage(utf8);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 67u, serialize("VisitorInfoMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("VisitorInfoMessage", DocumentProtocol::MESSAGE_VISITORINFO, lang);
        if (EXPECT_TRUE(obj)) {
            VisitorInfoMessage &ref = static_cast<VisitorInfoMessage&>(*obj);
            EXPECT_EQUAL(document::BucketId(16, 1), ref.getFinishedBuckets()[0]);
            EXPECT_EQUAL(document::BucketId(16, 2), ref.getFinishedBuckets()[1]);
            EXPECT_EQUAL(document::BucketId(16, 4), ref.getFinishedBuckets()[2]);
            EXPECT_EQUAL(utf8, ref.getErrorMessage());
        }
    }
    return true;
}

bool
Messages60Test::testDestroyVisitorReply()
{
    return tryDocumentReply("DestroyVisitorReply", DocumentProtocol::REPLY_DESTROYVISITOR);
}

bool
Messages60Test::testDocumentIgnoredReply()
{
    DocumentIgnoredReply tmp;
    serialize("DocumentIgnoredReply", tmp);
    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj(
                deserialize("DocumentIgnoredReply",
                            DocumentProtocol::REPLY_DOCUMENTIGNORED, lang));
        EXPECT_TRUE(obj);
    }
    return true;
}

bool
Messages60Test::testDocumentListReply()
{
    return tryVisitorReply("DocumentListReply", DocumentProtocol::REPLY_DOCUMENTLIST);
}

bool
Messages60Test::testGetDocumentReply()
{
    document::Document::SP doc =
        createDoc(getTypeRepo(), "testdoc", "id:ns:testdoc::");
    GetDocumentReply tmp(doc);

    EXPECT_EQUAL(128u, sizeof(GetDocumentReply));
    EXPECT_EQUAL((size_t)47, serialize("GetDocumentReply", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetDocumentReply", DocumentProtocol::REPLY_GETDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            GetDocumentReply &ref = static_cast<GetDocumentReply&>(*obj);

            EXPECT_EQUAL(string("testdoc"), ref.getDocument().getType().getName());
            EXPECT_EQUAL(string("id:ns:testdoc::"), ref.getDocument().getId().toString());
        }
    }
    return true;
}

bool
Messages60Test::testMapVisitorReply()
{
    return tryVisitorReply("MapVisitorReply", DocumentProtocol::REPLY_MAPVISITOR);
}

bool
Messages60Test::testStatBucketReply()
{
    StatBucketReply msg;
    msg.setResults("These are the votes of the Norwegian jury");

    EXPECT_EQUAL(50u, serialize("StatBucketReply", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("StatBucketReply", DocumentProtocol::REPLY_STATBUCKET, lang);
        if (EXPECT_TRUE(obj)) {
            StatBucketReply &ref = static_cast<StatBucketReply&>(*obj);
            EXPECT_EQUAL("These are the votes of the Norwegian jury", ref.getResults());
        }
    }
    return true;
}

bool
Messages60Test::testVisitorInfoReply()
{
    return tryVisitorReply("VisitorInfoReply", DocumentProtocol::REPLY_VISITORINFO);
}

bool
Messages60Test::testWrongDistributionReply()
{
    WrongDistributionReply tmp("distributor:3 storage:2");

    serialize("WrongDistributionReply", tmp);

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("WrongDistributionReply", DocumentProtocol::REPLY_WRONGDISTRIBUTION, lang);
        if (EXPECT_TRUE(obj)) {
            WrongDistributionReply &ref = static_cast<WrongDistributionReply&>(*obj);
            EXPECT_EQUAL(string("distributor:3 storage:2"), ref.getSystemState());
        }
    }
    return true;
}

bool
Messages60Test::testGetBucketListReply()
{
    GetBucketListReply reply;
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(16, 123), "foo"));
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(17, 1123), "bar"));
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(18, 11123), "zoink"));

    EXPECT_EQUAL(56u, serialize("GetBucketListReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketListReply", DocumentProtocol::REPLY_GETBUCKETLIST, lang);
        if (EXPECT_TRUE(obj)) {
            GetBucketListReply &ref = static_cast<GetBucketListReply&>(*obj);

            EXPECT_EQUAL(ref.getBuckets()[0], GetBucketListReply::BucketInfo(document::BucketId(16, 123), "foo"));
            EXPECT_EQUAL(ref.getBuckets()[1], GetBucketListReply::BucketInfo(document::BucketId(17, 1123), "bar"));
            EXPECT_EQUAL(ref.getBuckets()[2], GetBucketListReply::BucketInfo(document::BucketId(18, 11123), "zoink"));
        }
    }
    return true;
}

bool
Messages60Test::testGetBucketStateReply()
{
    document::GlobalId foo = document::DocumentId("id:ns:testdoc::foo").getGlobalId();
    document::GlobalId bar = document::DocumentId("id:ns:testdoc::bar").getGlobalId();

    GetBucketStateReply reply;
    reply.getBucketState().push_back(DocumentState(foo, 777, false));
    reply.getBucketState().push_back(DocumentState(bar, 888, true));
    EXPECT_EQUAL(53u, serialize("GetBucketStateReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketStateReply", DocumentProtocol::REPLY_GETBUCKETSTATE, lang);
        if (EXPECT_TRUE(obj)) {
            GetBucketStateReply &ref = static_cast<GetBucketStateReply&>(*obj);

            EXPECT_EQUAL(777u, ref.getBucketState()[0].getTimestamp());
            EXPECT_EQUAL(foo, ref.getBucketState()[0].getGlobalId());
            EXPECT_EQUAL(false, ref.getBucketState()[0].isRemoveEntry());
            EXPECT_EQUAL(888u, ref.getBucketState()[1].getTimestamp());
            EXPECT_EQUAL(bar, ref.getBucketState()[1].getGlobalId());
            EXPECT_EQUAL(true, ref.getBucketState()[1].isRemoveEntry());
        }
    }
    return true;
}

bool
Messages60Test::testEmptyBucketsReply()
{
    return tryVisitorReply("EmptyBucketsReply", DocumentProtocol::REPLY_EMPTYBUCKETS);
}

bool
Messages60Test::testRemoveLocationReply()
{
    DocumentReply tmp(DocumentProtocol::REPLY_REMOVELOCATION);

    EXPECT_EQUAL((uint32_t)5, serialize("RemoveLocationReply", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("RemoveLocationReply", DocumentProtocol::REPLY_REMOVELOCATION, lang);
        EXPECT_TRUE(obj);
    }
    return true;
}



////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

bool
Messages60Test::tryDocumentReply(const string &filename, uint32_t type)
{
    DocumentReply tmp(type);

    EXPECT_EQUAL((uint32_t)5, serialize(filename, tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize(filename, type, lang);
        if (EXPECT_TRUE(obj)) {
            DocumentReply *ref = dynamic_cast<DocumentReply*>(obj.get());
            EXPECT_TRUE(ref != NULL);
        }
    }
    return true;
}

bool
Messages60Test::tryVisitorReply(const string &filename, uint32_t type)
{
    VisitorReply tmp(type);

    EXPECT_EQUAL((uint32_t)5, serialize(filename, tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize(filename, type, lang);
        if (EXPECT_TRUE(obj)) {
            VisitorReply *ref = dynamic_cast<VisitorReply*>(obj.get());
            EXPECT_TRUE(ref != NULL);
        }
    }
    return true;
}
