// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messages50test.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>

using document::DataType;
using document::DocumentTypeRepo;

///////////////////////////////////////////////////////////////////////////////
//
// Setup
//
///////////////////////////////////////////////////////////////////////////////

Messages50Test::Messages50Test()
{
    // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
    // version 5.0. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.
    putTest(DocumentProtocol::MESSAGE_BATCHDOCUMENTUPDATE, TEST_METHOD(Messages50Test::testBatchDocumentUpdateMessage));
    putTest(DocumentProtocol::MESSAGE_CREATEVISITOR, TEST_METHOD(Messages50Test::testCreateVisitorMessage));
    putTest(DocumentProtocol::MESSAGE_DESTROYVISITOR, TEST_METHOD(Messages50Test::testDestroyVisitorMessage));
    putTest(DocumentProtocol::MESSAGE_DOCUMENTLIST, TEST_METHOD(Messages50Test::testDocumentListMessage));
    putTest(DocumentProtocol::MESSAGE_DOCUMENTSUMMARY, TEST_METHOD(Messages50Test::testDocumentSummaryMessage));
    putTest(DocumentProtocol::MESSAGE_EMPTYBUCKETS, TEST_METHOD(Messages50Test::testEmptyBucketsMessage));
    putTest(DocumentProtocol::MESSAGE_GETBUCKETLIST, TEST_METHOD(Messages50Test::testGetBucketListMessage));
    putTest(DocumentProtocol::MESSAGE_GETBUCKETSTATE, TEST_METHOD(Messages50Test::testGetBucketStateMessage));
    putTest(DocumentProtocol::MESSAGE_GETDOCUMENT, TEST_METHOD(Messages50Test::testGetDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_MAPVISITOR, TEST_METHOD(Messages50Test::testMapVisitorMessage));
    putTest(DocumentProtocol::MESSAGE_PUTDOCUMENT, TEST_METHOD(Messages50Test::testPutDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_QUERYRESULT, TEST_METHOD(Messages50Test::testQueryResultMessage));
    putTest(DocumentProtocol::MESSAGE_REMOVEDOCUMENT, TEST_METHOD(Messages50Test::testRemoveDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_REMOVELOCATION, TEST_METHOD(Messages50Test::testRemoveLocationMessage));
    putTest(DocumentProtocol::MESSAGE_SEARCHRESULT, TEST_METHOD(Messages50Test::testSearchResultMessage));
    putTest(DocumentProtocol::MESSAGE_STATBUCKET, TEST_METHOD(Messages50Test::testStatBucketMessage));
    putTest(DocumentProtocol::MESSAGE_UPDATEDOCUMENT, TEST_METHOD(Messages50Test::testUpdateDocumentMessage));
    putTest(DocumentProtocol::MESSAGE_VISITORINFO, TEST_METHOD(Messages50Test::testVisitorInfoMessage));

    putTest(DocumentProtocol::REPLY_BATCHDOCUMENTUPDATE, TEST_METHOD(Messages50Test::testBatchDocumentUpdateReply));
    putTest(DocumentProtocol::REPLY_CREATEVISITOR, TEST_METHOD(Messages50Test::testCreateVisitorReply));
    putTest(DocumentProtocol::REPLY_DESTROYVISITOR, TEST_METHOD(Messages50Test::testDestroyVisitorReply));
    putTest(DocumentProtocol::REPLY_DOCUMENTLIST, TEST_METHOD(Messages50Test::testDocumentListReply));
    putTest(DocumentProtocol::REPLY_DOCUMENTSUMMARY, TEST_METHOD(Messages50Test::testDocumentSummaryReply));
    putTest(DocumentProtocol::REPLY_EMPTYBUCKETS, TEST_METHOD(Messages50Test::testEmptyBucketsReply));
    putTest(DocumentProtocol::REPLY_GETBUCKETLIST, TEST_METHOD(Messages50Test::testGetBucketListReply));
    putTest(DocumentProtocol::REPLY_GETBUCKETSTATE, TEST_METHOD(Messages50Test::testGetBucketStateReply));
    putTest(DocumentProtocol::REPLY_GETDOCUMENT, TEST_METHOD(Messages50Test::testGetDocumentReply));
    putTest(DocumentProtocol::REPLY_MAPVISITOR, TEST_METHOD(Messages50Test::testMapVisitorReply));
    putTest(DocumentProtocol::REPLY_PUTDOCUMENT, TEST_METHOD(Messages50Test::testPutDocumentReply));
    putTest(DocumentProtocol::REPLY_QUERYRESULT, TEST_METHOD(Messages50Test::testQueryResultReply));
    putTest(DocumentProtocol::REPLY_REMOVEDOCUMENT, TEST_METHOD(Messages50Test::testRemoveDocumentReply));
    putTest(DocumentProtocol::REPLY_REMOVELOCATION, TEST_METHOD(Messages50Test::testRemoveLocationReply));
    putTest(DocumentProtocol::REPLY_SEARCHRESULT, TEST_METHOD(Messages50Test::testSearchResultReply));
    putTest(DocumentProtocol::REPLY_STATBUCKET, TEST_METHOD(Messages50Test::testStatBucketReply));
    putTest(DocumentProtocol::REPLY_UPDATEDOCUMENT, TEST_METHOD(Messages50Test::testUpdateDocumentReply));
    putTest(DocumentProtocol::REPLY_VISITORINFO, TEST_METHOD(Messages50Test::testVisitorInfoReply));
    putTest(DocumentProtocol::REPLY_WRONGDISTRIBUTION, TEST_METHOD(Messages50Test::testWrongDistributionReply));
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
    return document::Document::SP(new document::Document(
                    *repo.getDocumentType(type_name),
                    document::DocumentId(id)));
}

}  // namespace

bool
Messages50Test::testGetBucketListMessage()
{
    GetBucketListMessage msg(document::BucketId(16, 123));
    msg.setLoadType(_loadTypes["foo"]);
    EXPECT_EQUAL(string("foo"), msg.getLoadType().getName());
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 12u, serialize("GetBucketListMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketListMessage", DocumentProtocol::MESSAGE_GETBUCKETLIST, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            GetBucketListMessage &ref = static_cast<GetBucketListMessage&>(*obj);
            EXPECT_EQUAL(string("foo"), ref.getLoadType().getName());
            EXPECT_EQUAL(document::BucketId(16, 123), ref.getBucketId());
        }
    }
    return true;
}

bool
Messages50Test::testEmptyBucketsMessage()
{
    std::vector<document::BucketId> bids;
    for (size_t i=0; i < 13; ++i) {
        bids.push_back(document::BucketId(16, i));
    }

    EmptyBucketsMessage msg(bids);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 112u, serialize("EmptyBucketsMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("EmptyBucketsMessage", DocumentProtocol::MESSAGE_EMPTYBUCKETS, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            EmptyBucketsMessage &ref = static_cast<EmptyBucketsMessage&>(*obj);
            for (size_t i=0; i < 13; ++i) {
                EXPECT_EQUAL(document::BucketId(16, i), ref.getBucketIds()[i]);
            }
        }
    }
    return true;
}


bool
Messages50Test::testStatBucketMessage()
{
    StatBucketMessage msg(document::BucketId(16, 123), "id.user=123");

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 27u, serialize("StatBucketMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("StatBucketMessage", DocumentProtocol::MESSAGE_STATBUCKET, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            StatBucketMessage &ref = static_cast<StatBucketMessage&>(*obj);
            EXPECT_EQUAL(document::BucketId(16, 123), ref.getBucketId());
            EXPECT_EQUAL("id.user=123", ref.getDocumentSelection());
        }
    }
    return true;
}

bool
Messages50Test::testCreateVisitorMessage() {
    CreateVisitorMessage tmp("SomeLibrary", "myvisitor", "newyork", "london");
    tmp.setDocumentSelection("true and false or true");
    tmp.getParameters().set("myvar", "somevalue");
    tmp.getParameters().set("anothervar", uint64_t(34));
    tmp.getBuckets().push_back(document::BucketId(16, 1234));
    tmp.setVisitRemoves(true);
    tmp.setVisitorOrdering(document::OrderingSpecification::DESCENDING);
    tmp.setMaxBucketsPerVisitor(2);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)168, serialize("CreateVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("CreateVisitorMessage", DocumentProtocol::MESSAGE_CREATEVISITOR, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            CreateVisitorMessage &ref = static_cast<CreateVisitorMessage&>(*obj);

            EXPECT_EQUAL(string("SomeLibrary"), ref.getLibraryName());
            EXPECT_EQUAL(string("myvisitor"), ref.getInstanceId());
            EXPECT_EQUAL(string("newyork"), ref.getControlDestination());
            EXPECT_EQUAL(string("london"), ref.getDataDestination());
            EXPECT_EQUAL(string("true and false or true"), ref.getDocumentSelection());
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
Messages50Test::testDestroyVisitorMessage()
{
    DestroyVisitorMessage tmp("myvisitor");

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)17, serialize("DestroyVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("DestroyVisitorMessage", DocumentProtocol::MESSAGE_DESTROYVISITOR, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            DestroyVisitorMessage &ref = static_cast<DestroyVisitorMessage&>(*obj);
            EXPECT_EQUAL(string("myvisitor"), ref.getInstanceId());
        }
    }
    return true;
}

bool
Messages50Test::testDocumentListMessage()
{
    document::Document::SP doc =
        createDoc(getTypeRepo(), "testdoc", "userdoc:scheme:1234:");
    DocumentListMessage::Entry entry(1234, doc, false);

    DocumentListMessage tmp(document::BucketId(16, 1234));
    tmp.getDocuments().push_back(entry);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)63, serialize("DocumentListMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("DocumentListMessage", DocumentProtocol::MESSAGE_DOCUMENTLIST, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            DocumentListMessage &ref = static_cast<DocumentListMessage&>(*obj);

            EXPECT_EQUAL("userdoc:scheme:1234:", ref.getDocuments()[0].getDocument()->getId().toString());
            EXPECT_EQUAL(1234, ref.getDocuments()[0].getTimestamp());
            EXPECT_TRUE(!ref.getDocuments()[0].isRemoveEntry());
        }
    }
    return true;
}


bool
Messages50Test::testRemoveLocationMessage()
{
    {
        document::BucketIdFactory factory;
        document::select::Parser parser(getTypeRepo(), factory);
        RemoveLocationMessage msg(factory, parser, "id.group == \"mygroup\"");

        EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 29u, serialize("RemoveLocationMessage", msg));
        for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
            mbus::Routable::UP obj = deserialize("RemoveLocationMessage", DocumentProtocol::MESSAGE_REMOVELOCATION, lang);
            if (EXPECT_TRUE(obj.get() != NULL)) {
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
Messages50Test::testDocumentSummaryMessage()
{
    DocumentSummaryMessage srm;
    EXPECT_EQUAL(srm.hasSequenceId(), false);
    EXPECT_EQUAL(srm.getSummaryCount(), size_t(0));

    mbus::Blob data = encode(srm);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + size_t(12), data.size());

    writeFile(getPath("5-cpp-DocumentSummaryMessage-1.dat"), data);
    // print(data);

    mbus::Routable::UP routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_DOCUMENTSUMMARY);
    DocumentSummaryMessage * dm = static_cast<DocumentSummaryMessage *>(routable.get());
    EXPECT_EQUAL(dm->getSummaryCount(), size_t(0));

    srm.addSummary("doc1", "summary1", 8);
    srm.addSummary("aoc17", "summary45", 9);

    data = encode(srm);
    //print(data);

    const void *summary(NULL);
    const char *docId(NULL);
    size_t sz(0);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 52u, data.size());
    writeFile(getPath("5-cpp-DocumentSummaryMessage-2.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_DOCUMENTSUMMARY);
    dm = static_cast<DocumentSummaryMessage *>(routable.get());
    EXPECT_EQUAL(dm->getSummaryCount(), size_t(2));
    dm->getSummary(0, docId, summary, sz);
    EXPECT_EQUAL(sz, 8u);
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    EXPECT_EQUAL(memcmp("summary1", summary, sz), 0);
    dm->getSummary(1, docId, summary, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(strcmp("aoc17", docId), 0);
    EXPECT_EQUAL(memcmp("summary45", summary, sz), 0);

    srm.sort();

    data = encode(srm);
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 52u, data.size());
    writeFile(getPath("5-cpp-DocumentSummaryMessage-3.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_DOCUMENTSUMMARY);
    dm = static_cast<DocumentSummaryMessage *>(routable.get());
    EXPECT_EQUAL(dm->getSummaryCount(), size_t(2));
    dm->getSummary(0, docId, summary, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(strcmp("aoc17", docId), 0);
    EXPECT_EQUAL(memcmp("summary45", summary, sz), 0);
    dm->getSummary(1, docId, summary, sz);
    EXPECT_EQUAL(sz, 8u);
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    EXPECT_EQUAL(memcmp("summary1", summary, sz), 0);
    return true;
}

bool
Messages50Test::testGetDocumentMessage()
{
    GetDocumentMessage tmp(document::DocumentId("doc:scheme:"), 0);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)20, serialize("GetDocumentMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetDocumentMessage", DocumentProtocol::MESSAGE_GETDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            GetDocumentMessage &ref = static_cast<GetDocumentMessage&>(*obj);
            EXPECT_EQUAL(string("doc:scheme:"), ref.getDocumentId().toString());
        }
    }
    return true;
}

bool
Messages50Test::testMapVisitorMessage()
{
    MapVisitorMessage tmp;
    tmp.getData().set("foo", 3);
    tmp.getData().set("bar", 5);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)32, serialize("MapVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("MapVisitorMessage", DocumentProtocol::MESSAGE_MAPVISITOR, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            MapVisitorMessage &ref = static_cast<MapVisitorMessage&>(*obj);
            EXPECT_EQUAL(3, ref.getData().get("foo", 0));
            EXPECT_EQUAL(5, ref.getData().get("bar", 0));
        }
    }
    return true;
}

bool
Messages50Test::testCreateVisitorReply()
{
    CreateVisitorReply reply(DocumentProtocol::REPLY_CREATEVISITOR);
    reply.setLastBucket(document::BucketId(16, 123));
    vdslib::VisitorStatistics vs;
    vs.setBucketsVisited(3);
    vs.setDocumentsVisited(1000);
    vs.setBytesVisited(1024000);
    vs.setDocumentsReturned(123);
    vs.setBytesReturned(512000);
    vs.setSecondPassDocumentsReturned(456);
    vs.setSecondPassBytesReturned(789100);
    reply.setVisitorStatistics(vs);

    EXPECT_EQUAL(65u, serialize("CreateVisitorReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("CreateVisitorReply", DocumentProtocol::REPLY_CREATEVISITOR, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            CreateVisitorReply &ref = static_cast<CreateVisitorReply&>(*obj);

            EXPECT_EQUAL(ref.getLastBucket(), document::BucketId(16, 123));
            EXPECT_EQUAL(ref.getVisitorStatistics().getBucketsVisited(), (uint32_t)3);
            EXPECT_EQUAL(ref.getVisitorStatistics().getDocumentsVisited(), (uint64_t)1000);
            EXPECT_EQUAL(ref.getVisitorStatistics().getBytesVisited(), (uint64_t)1024000);
            EXPECT_EQUAL(ref.getVisitorStatistics().getDocumentsReturned(), (uint64_t)123);
            EXPECT_EQUAL(ref.getVisitorStatistics().getBytesReturned(), (uint64_t)512000);
            EXPECT_EQUAL(ref.getVisitorStatistics().getSecondPassDocumentsReturned(), (uint64_t)456);
            EXPECT_EQUAL(ref.getVisitorStatistics().getSecondPassBytesReturned(), (uint64_t)789100);
        }
    }
    return true;
}

bool
Messages50Test::testPutDocumentMessage()
{
    document::Document::SP doc =
        createDoc(getTypeRepo(), "testdoc", "doc:scheme:");
    PutDocumentMessage msg(doc);

    msg.setTimestamp(666);
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 41u, serialize("PutDocumentMessage", msg));
    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("PutDocumentMessage", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            PutDocumentMessage &ref = static_cast<PutDocumentMessage&>(*obj);
            EXPECT_TRUE(ref.getDocument().getType().getName() == "testdoc");
            EXPECT_TRUE(ref.getDocument().getId().toString() == "doc:scheme:");
            EXPECT_EQUAL(666u, ref.getTimestamp());
            EXPECT_EQUAL(37u, ref.getApproxSize());
        }
    }
    return true;
}

bool
Messages50Test::testGetBucketStateMessage()
{
    GetBucketStateMessage tmp;
    tmp.setBucketId(document::BucketId(16, 666));
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 12u, serialize("GetBucketStateMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketStateMessage", DocumentProtocol::MESSAGE_GETBUCKETSTATE, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            GetBucketStateMessage &ref = static_cast<GetBucketStateMessage&>(*obj);

            EXPECT_EQUAL(16u, ref.getBucketId().getUsedBits());
            EXPECT_EQUAL(4611686018427388570ull, ref.getBucketId().getId());
        }
    }
    return true;
}

bool
Messages50Test::testPutDocumentReply()
{
    WriteDocumentReply reply(DocumentProtocol::REPLY_PUTDOCUMENT);
    reply.setHighestModificationTimestamp(30);

    EXPECT_EQUAL(13u, serialize("PutDocumentReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("PutDocumentReply", DocumentProtocol::REPLY_PUTDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            WriteDocumentReply &ref = static_cast<WriteDocumentReply&>(*obj);
            EXPECT_EQUAL(30u, ref.getHighestModificationTimestamp());
        }
    }
    return true;
}

bool
Messages50Test::testUpdateDocumentReply()
{
    UpdateDocumentReply reply;
    reply.setWasFound(false);
    reply.setHighestModificationTimestamp(30);

    EXPECT_EQUAL(14u, serialize("UpdateDocumentReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("UpdateDocumentReply", DocumentProtocol::REPLY_UPDATEDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            UpdateDocumentReply &ref = static_cast<UpdateDocumentReply&>(*obj);
            EXPECT_EQUAL(30u, ref.getHighestModificationTimestamp());
            EXPECT_EQUAL(false, ref.wasFound());
        }
    }
    return true;
}

bool
Messages50Test::testRemoveDocumentMessage()
{
    RemoveDocumentMessage tmp(document::DocumentId("doc:scheme:"));

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + (size_t)16, serialize("RemoveDocumentMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("RemoveDocumentMessage", DocumentProtocol::MESSAGE_REMOVEDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            RemoveDocumentMessage &ref = static_cast<RemoveDocumentMessage&>(*obj);
            EXPECT_EQUAL(string("doc:scheme:"), ref.getDocumentId().toString());
        }
    }
    return true;
}

bool
Messages50Test::testRemoveDocumentReply()
{
    RemoveDocumentReply reply;
    std::vector<uint64_t> ts;
    reply.setWasFound(false);
    reply.setHighestModificationTimestamp(30);

    EXPECT_EQUAL(14u, serialize("RemoveDocumentReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("RemoveDocumentReply", DocumentProtocol::REPLY_REMOVEDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            RemoveDocumentReply &ref = static_cast<RemoveDocumentReply&>(*obj);
            EXPECT_EQUAL(30u, ref.getHighestModificationTimestamp());
            EXPECT_EQUAL(false, ref.wasFound());
        }
    }
    return true;
}

bool
Messages50Test::testSearchResultMessage()
{
    SearchResultMessage srm;
    EXPECT_EQUAL(srm.getSequenceId(), 0u);
    EXPECT_EQUAL(srm.getHitCount(), 0u);
    EXPECT_EQUAL(srm.getAggregatorList().getSerializedSize(), 4u);
    EXPECT_EQUAL(srm.vdslib::SearchResult::getSerializedSize(), 20u);
    EXPECT_EQUAL(srm.getSerializedSize(), 20u);

    mbus::Blob data = encode(srm);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + size_t(24), data.size());

    writeFile(getPath("5-cpp-SearchResultMessage-1.dat"), data);
    // print(data);

    mbus::Routable::UP routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_SEARCHRESULT);
    SearchResultMessage * dm = static_cast<SearchResultMessage *>(routable.get());
    EXPECT_EQUAL(dm->getSequenceId(), size_t(0));
    EXPECT_EQUAL(dm->getHitCount(), size_t(0));

    srm.addHit(0, "doc1", 89);
    srm.addHit(1, "doc17", 109);
    //srm.setSequenceId(567);

    data = encode(srm);
    //EXPECT_EQUAL(srm.getSequenceId(), size_t(567));

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 55u, data.size());
    writeFile(getPath("5-cpp-SearchResultMessage-2.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_SEARCHRESULT);
    dm = static_cast<SearchResultMessage *>(routable.get());
//    EXPECT_EQUAL(dm->getSequenceId(), size_t(567));
    EXPECT_EQUAL(dm->getHitCount(), size_t(2));
    const char *docId;
    SearchResultMessage::RankType rank;
    dm->getHit(0, docId, rank);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    dm->getHit(1, docId, rank);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);

    srm.sort();

    data = encode(srm);
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 55u, data.size());
    writeFile(getPath("5-cpp-SearchResultMessage-3.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_SEARCHRESULT);
    dm = static_cast<SearchResultMessage *>(routable.get());
//    EXPECT_EQUAL(dm->getSequenceId(), size_t(567));
    EXPECT_EQUAL(dm->getHitCount(), size_t(2));
    dm->getHit(0, docId, rank);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);
    dm->getHit(1, docId, rank);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);

    SearchResultMessage srm2;
    srm2.addHit(0, "doc1", 89, "sortdata2", 9);
    srm2.addHit(1, "doc17", 109, "sortdata1", 9);
    srm2.addHit(2, "doc18", 90, "sortdata3", 9);
    //srm2.setSequenceId(567);
    data = encode(srm2);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 108u, data.size());
    writeFile(getPath("5-cpp-SearchResultMessage-4.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_SEARCHRESULT);
    dm = static_cast<SearchResultMessage *>(routable.get());
    //EXPECT_EQUAL(dm->getSequenceId(), size_t(567));
    EXPECT_EQUAL(dm->getHitCount(), size_t(3));
    dm->getHit(0, docId, rank);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    dm->getHit(1, docId, rank);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);
    dm->getHit(2, docId, rank);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(90));
    EXPECT_EQUAL(strcmp("doc18", docId), 0);

    srm2.sort();
    const void *buf;
    size_t sz;
    srm2.getHit(0, docId, rank);
    srm2.getSortBlob(0, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata1", buf, sz), 0);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);
    srm2.getHit(1, docId, rank);
    srm2.getSortBlob(1, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata2", buf, sz), 0);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    srm2.getHit(2, docId, rank);
    srm2.getSortBlob(2, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata3", buf, sz), 0);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(90));
    EXPECT_EQUAL(strcmp("doc18", docId), 0);

    data = encode(srm2);
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 108u, data.size());
    writeFile(getPath("5-cpp-SearchResultMessage-5.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_SEARCHRESULT);
    dm = static_cast<SearchResultMessage *>(routable.get());
//    EXPECT_EQUAL(dm->getSequenceId(), size_t(567));
    EXPECT_EQUAL(dm->getHitCount(), size_t(3));
    dm->getHit(0, docId, rank);
    dm->getSortBlob(0, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata1", buf, sz), 0);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", docId), 0);
    dm->getHit(1, docId, rank);
    dm->getSortBlob(1, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata2", buf, sz), 0);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", docId), 0);
    dm->getHit(2, docId, rank);
    dm->getSortBlob(2, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata3", buf, sz), 0);
    EXPECT_EQUAL(rank, SearchResultMessage::RankType(90));
    EXPECT_EQUAL(strcmp("doc18", docId), 0);
    return true;
}

bool
Messages50Test::testUpdateDocumentMessage()
{
    const DocumentTypeRepo &repo = getTypeRepo();
    const document::DocumentType &docType = *repo.getDocumentType("testdoc");
    auto upd(std::make_shared<document::DocumentUpdate>(repo, docType, document::DocumentId("doc:scheme:")));
    upd->addFieldPathUpdate(document::FieldPathUpdate::CP(
            new document::RemoveFieldPathUpdate("intfield", "testdoc.intfield > 0")));
    UpdateDocumentMessage msg(upd);
    msg.setOldTimestamp(666u);
    msg.setNewTimestamp(777u);
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 89u, serialize("UpdateDocumentMessage", msg));
    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("UpdateDocumentMessage", DocumentProtocol::MESSAGE_UPDATEDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != nullptr)) {
            UpdateDocumentMessage &ref = static_cast<UpdateDocumentMessage&>(*obj);
            EXPECT_EQUAL(*upd, ref.getDocumentUpdate());
            EXPECT_EQUAL(666u, ref.getOldTimestamp());
            EXPECT_EQUAL(777u, ref.getNewTimestamp());
            EXPECT_EQUAL(85u, ref.getApproxSize());
        }
    }
    return true;
}

bool
Messages50Test::testBatchDocumentUpdateMessage()
{
    const DocumentTypeRepo &repo = getTypeRepo();
    const document::DocumentType &docType = *repo.getDocumentType("testdoc");

    BatchDocumentUpdateMessage msg(1234);

    {
        document::DocumentUpdate::SP upd;
        upd.reset(new document::DocumentUpdate(repo, docType, document::DocumentId("userdoc:footype:1234:foo")));
        upd->addFieldPathUpdate(document::FieldPathUpdate::CP(
                        new document::RemoveFieldPathUpdate("intfield", "testdoc.intfield > 0")));
        msg.addUpdate(upd);
    }
    {
        document::DocumentUpdate::SP upd;
        upd.reset(new document::DocumentUpdate(repo, docType, document::DocumentId("orderdoc(32,17):footype:1234:123456789:foo")));
        upd->addFieldPathUpdate(document::FieldPathUpdate::CP(
                        new document::RemoveFieldPathUpdate("intfield", "testdoc.intfield > 0")));
        msg.addUpdate(upd);
    }
    try {
        document::DocumentUpdate::SP upd;
        upd.reset(new document::DocumentUpdate(repo, docType, document::DocumentId("userdoc:footype:5678:foo")));
        upd->addFieldPathUpdate(document::FieldPathUpdate::CP(
                        new document::RemoveFieldPathUpdate("intfield", "testdoc.intfield > 0")));
        msg.addUpdate(upd);
        EXPECT_TRUE(false);
    } catch (...) {
    }
    try {
        document::DocumentUpdate::SP upd;
        upd.reset(new document::DocumentUpdate(repo, docType, document::DocumentId("groupdoc:footype:hable:foo")));
        upd->addFieldPathUpdate(document::FieldPathUpdate::CP(
                        new document::RemoveFieldPathUpdate("intfield", "testdoc.intfield > 0")));
        msg.addUpdate(upd);
        EXPECT_TRUE(false);
    } catch (...) {
    }

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 202u, serialize("BatchDocumentUpdateMessage", msg));
    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("BatchDocumentUpdateMessage", DocumentProtocol::MESSAGE_BATCHDOCUMENTUPDATE, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            BatchDocumentUpdateMessage &ref = static_cast<BatchDocumentUpdateMessage&>(*obj);
            EXPECT_EQUAL(2u, ref.getUpdates().size());
        }
    }

    return true;
}

bool
Messages50Test::testBatchDocumentUpdateReply()
{
    BatchDocumentUpdateReply reply;
    reply.setHighestModificationTimestamp(30);
    {
        std::vector<bool> notFound(3);
        notFound[0] = false;
        notFound[1] = true;
        notFound[2] = true;
        reply.getDocumentsNotFound() = notFound;
    }

    EXPECT_EQUAL(20u, serialize("BatchDocumentUpdateReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("BatchDocumentUpdateReply", DocumentProtocol::REPLY_BATCHDOCUMENTUPDATE, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            BatchDocumentUpdateReply &ref = dynamic_cast<BatchDocumentUpdateReply&>(*obj);
            EXPECT_EQUAL(30u, ref.getHighestModificationTimestamp());
            {
                const std::vector<bool>& notFound = ref.getDocumentsNotFound();
                EXPECT_TRUE(notFound[0] == false);
                EXPECT_TRUE(notFound[1] == true);
                EXPECT_TRUE(notFound[2] == true);
            }
        }
    }
    return true;
}

bool
Messages50Test::testQueryResultMessage()
{
    QueryResultMessage srm;
    vdslib::SearchResult & sr(srm.getSearchResult());
    EXPECT_EQUAL(srm.getSequenceId(), 0u);
    EXPECT_EQUAL(sr.getHitCount(), 0u);
    EXPECT_EQUAL(sr.getAggregatorList().getSerializedSize(), 4u);
    EXPECT_EQUAL(sr.getSerializedSize(), 20u);
    EXPECT_EQUAL(srm.getApproxSize(), 28u);

    mbus::Blob data = encode(srm);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + size_t(32), data.size());

    writeFile(getPath("5-cpp-QueryResultMessage-1.dat"), data);
    // print(data);

    mbus::Routable::UP routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_QUERYRESULT);
    QueryResultMessage * dm = static_cast<QueryResultMessage *>(routable.get());
    vdslib::SearchResult * dr(&dm->getSearchResult());
    EXPECT_EQUAL(dm->getSequenceId(), size_t(0));
    EXPECT_EQUAL(dr->getHitCount(), size_t(0));

    sr.addHit(0, "doc1", 89);
    sr.addHit(1, "doc17", 109);

    data = encode(srm);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 63u, data.size());
    writeFile(getPath("5-cpp-QueryResultMessage-2.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_QUERYRESULT);
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

    data = encode(srm);
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 63u, data.size());
    writeFile(getPath("5-cpp-QueryResultMessage-3.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_QUERYRESULT);
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
    data = encode(srm2);

    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 116u, data.size());
    writeFile(getPath("5-cpp-QueryResultMessage-4.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_QUERYRESULT);
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

    data = encode(srm2);
    EXPECT_EQUAL(MESSAGE_BASE_LENGTH + 116u, data.size());
    writeFile(getPath("5-cpp-QueryResultMessage-5.dat"), data);
    routable = decode(data);
    if (!EXPECT_TRUE(routable.get() != NULL)) {
        return false;
    }
    EXPECT_EQUAL(routable->getType(), (uint32_t)DocumentProtocol::MESSAGE_QUERYRESULT);
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
    return true;
}

bool
Messages50Test::testQueryResultReply()
{
    return tryVisitorReply("QueryResultReply", DocumentProtocol::REPLY_QUERYRESULT);
}

bool
Messages50Test::testVisitorInfoMessage()
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
        if (EXPECT_TRUE(obj.get() != NULL)) {
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
Messages50Test::testDestroyVisitorReply()
{
    return tryDocumentReply("DestroyVisitorReply", DocumentProtocol::REPLY_DESTROYVISITOR);
}

bool
Messages50Test::testDocumentListReply()
{
    return tryVisitorReply("DocumentListReply", DocumentProtocol::REPLY_DOCUMENTLIST);
}

bool
Messages50Test::testDocumentSummaryReply()
{
    return tryVisitorReply("DocumentSummaryReply", DocumentProtocol::REPLY_DOCUMENTSUMMARY);
}

bool
Messages50Test::testGetDocumentReply()
{
    document::Document::SP doc =
        createDoc(getTypeRepo(), "testdoc", "doc:scheme:");
    GetDocumentReply tmp(doc);

    EXPECT_EQUAL((size_t)43, serialize("GetDocumentReply", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetDocumentReply", DocumentProtocol::REPLY_GETDOCUMENT, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            GetDocumentReply &ref = static_cast<GetDocumentReply&>(*obj);

            EXPECT_EQUAL(string("testdoc"), ref.getDocument().getType().getName());
            EXPECT_EQUAL(string("doc:scheme:"), ref.getDocument().getId().toString());
        }
    }
    return true;
}

bool
Messages50Test::testMapVisitorReply()
{
    return tryVisitorReply("MapVisitorReply", DocumentProtocol::REPLY_MAPVISITOR);
}

bool
Messages50Test::testSearchResultReply()
{
    return tryVisitorReply("SearchResultReply", DocumentProtocol::REPLY_SEARCHRESULT);
}

bool
Messages50Test::testStatBucketReply()
{
    StatBucketReply msg;
    msg.setResults("These are the votes of the Norwegian jury");

    EXPECT_EQUAL(50u, serialize("StatBucketReply", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("StatBucketReply", DocumentProtocol::REPLY_STATBUCKET, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            StatBucketReply &ref = static_cast<StatBucketReply&>(*obj);
            EXPECT_EQUAL("These are the votes of the Norwegian jury", ref.getResults());
        }
    }
    return true;
}

bool
Messages50Test::testVisitorInfoReply()
{
    return tryVisitorReply("VisitorInfoReply", DocumentProtocol::REPLY_VISITORINFO);
}

bool
Messages50Test::testWrongDistributionReply()
{
    WrongDistributionReply tmp("distributor:3 storage:2");

    serialize("WrongDistributionReply", tmp);

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("WrongDistributionReply", DocumentProtocol::REPLY_WRONGDISTRIBUTION, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            WrongDistributionReply &ref = static_cast<WrongDistributionReply&>(*obj);
            EXPECT_EQUAL(string("distributor:3 storage:2"), ref.getSystemState());
        }
    }
    return true;
}

bool
Messages50Test::testGetBucketListReply()
{
    GetBucketListReply reply;
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(16, 123), "foo"));
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(17, 1123), "bar"));
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(18, 11123), "zoink"));

    EXPECT_EQUAL(56u, serialize("GetBucketListReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketListReply", DocumentProtocol::REPLY_GETBUCKETLIST, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            GetBucketListReply &ref = static_cast<GetBucketListReply&>(*obj);

            EXPECT_EQUAL(ref.getBuckets()[0], GetBucketListReply::BucketInfo(document::BucketId(16, 123), "foo"));
            EXPECT_EQUAL(ref.getBuckets()[1], GetBucketListReply::BucketInfo(document::BucketId(17, 1123), "bar"));
            EXPECT_EQUAL(ref.getBuckets()[2], GetBucketListReply::BucketInfo(document::BucketId(18, 11123), "zoink"));
        }
    }
    return true;
}

bool
Messages50Test::testGetBucketStateReply()
{
    document::GlobalId foo = document::DocumentId("doc:scheme:foo").getGlobalId();
    document::GlobalId bar = document::DocumentId("doc:scheme:bar").getGlobalId();

    GetBucketStateReply reply;
    reply.getBucketState().push_back(DocumentState(foo, 777, false));
    reply.getBucketState().push_back(DocumentState(bar, 888, true));
    EXPECT_EQUAL(53u, serialize("GetBucketStateReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketStateReply", DocumentProtocol::REPLY_GETBUCKETSTATE, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
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
Messages50Test::testEmptyBucketsReply()
{
    return tryVisitorReply("EmptyBucketsReply", DocumentProtocol::REPLY_EMPTYBUCKETS);
}

bool
Messages50Test::testRemoveLocationReply()
{
    DocumentReply tmp(DocumentProtocol::REPLY_REMOVELOCATION);

    EXPECT_EQUAL((uint32_t)5, serialize("RemoveLocationReply", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("RemoveLocationReply", DocumentProtocol::REPLY_REMOVELOCATION, lang);
        EXPECT_TRUE(obj.get() != NULL);
    }
    return true;
}



////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

bool
Messages50Test::tryDocumentReply(const string &filename, uint32_t type)
{
    DocumentReply tmp(type);

    EXPECT_EQUAL((uint32_t)5, serialize(filename, tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize(filename, type, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            DocumentReply *ref = dynamic_cast<DocumentReply*>(obj.get());
            EXPECT_TRUE(ref != NULL);
        }
    }
    return true;
}

bool
Messages50Test::tryVisitorReply(const string &filename, uint32_t type)
{
    VisitorReply tmp(type);

    EXPECT_EQUAL((uint32_t)5, serialize(filename, tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize(filename, type, lang);
        if (EXPECT_TRUE(obj.get() != NULL)) {
            VisitorReply *ref = dynamic_cast<VisitorReply*>(obj.get());
            EXPECT_TRUE(ref != NULL);
        }
    }
    return true;
}
