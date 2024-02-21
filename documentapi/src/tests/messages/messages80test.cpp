// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "testbase.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/featureset.h>
#include <array>

using document::DataType;
using document::DocumentTypeRepo;
using vespalib::FeatureValues;

// TODO rewrite to GTest!
class Messages80Test : public TestBase {
protected:
    vespalib::Version getVersion() const override {
        // Must be as high--or higher--than the v8 protocol version specified in documentprocotol.cpp
        // (and equal to its corresponding value in the Java implementation).
        return {8, 310};
    }
    bool shouldTestCoverage() const override { return true; }

    bool try_visitor_reply(const string& filename, uint32_t type);

    static constexpr std::array<uint32_t, 2> languages() noexcept {
        return {TestBase::LANG_CPP, TestBase::LANG_JAVA};
    }

public:
    Messages80Test();
    ~Messages80Test() override = default;

    bool test_create_visitor_message();
    bool test_create_visitor_reply();
    bool test_destroy_visitor_message();
    bool test_destroy_visitor_reply();
    bool test_document_ignored_reply();
    bool test_document_list_message();
    bool test_document_list_reply();
    bool test_empty_buckets_message();
    bool test_empty_buckets_reply();
    bool test_get_bucket_list_message();
    bool test_get_bucket_list_reply();
    bool test_get_bucket_state_message();
    bool test_get_bucket_state_reply();
    bool test_get_document_message();
    bool test_get_document_reply();
    bool test_map_visitor_message();
    bool test_map_visitor_reply();
    bool test_put_document_message();
    bool test_put_document_reply();
    bool test_query_result_message();
    bool test_query_result_reply();
    bool test_remove_document_message();
    bool test_remove_document_reply();
    bool test_remove_location_message();
    bool test_remove_location_reply();
    bool test_stat_bucket_message();
    bool test_stat_bucket_reply();
    bool test_update_document_message();
    bool test_update_document_reply();
    bool test_visitor_info_message();
    bool test_visitor_info_reply();
    bool test_wrong_distribution_reply();

    void do_test_get_reply_with_doc();
    void do_test_empty_get_reply();
};

namespace {

std::vector<char> doc1_mf_data{'H', 'i'};
std::vector<char> doc2_mf_data{'T', 'h', 'e', 'r', 'e'};

}

Messages80Test::Messages80Test() {
    putTest(DocumentProtocol::MESSAGE_CREATEVISITOR,   TEST_METHOD(Messages80Test::test_create_visitor_message));
    putTest(DocumentProtocol::MESSAGE_DESTROYVISITOR,  TEST_METHOD(Messages80Test::test_destroy_visitor_message));
    putTest(DocumentProtocol::MESSAGE_DOCUMENTLIST,    TEST_METHOD(Messages80Test::test_document_list_message));
    putTest(DocumentProtocol::MESSAGE_EMPTYBUCKETS,    TEST_METHOD(Messages80Test::test_empty_buckets_message));
    putTest(DocumentProtocol::MESSAGE_GETBUCKETLIST,   TEST_METHOD(Messages80Test::test_get_bucket_list_message));
    putTest(DocumentProtocol::MESSAGE_GETBUCKETSTATE,  TEST_METHOD(Messages80Test::test_get_bucket_state_message));
    putTest(DocumentProtocol::MESSAGE_GETDOCUMENT,     TEST_METHOD(Messages80Test::test_get_document_message));
    putTest(DocumentProtocol::MESSAGE_MAPVISITOR,      TEST_METHOD(Messages80Test::test_map_visitor_message));
    putTest(DocumentProtocol::MESSAGE_PUTDOCUMENT,     TEST_METHOD(Messages80Test::test_put_document_message));
    putTest(DocumentProtocol::MESSAGE_QUERYRESULT,     TEST_METHOD(Messages80Test::test_query_result_message));
    putTest(DocumentProtocol::MESSAGE_REMOVEDOCUMENT,  TEST_METHOD(Messages80Test::test_remove_document_message));
    putTest(DocumentProtocol::MESSAGE_REMOVELOCATION,  TEST_METHOD(Messages80Test::test_remove_location_message));
    putTest(DocumentProtocol::MESSAGE_STATBUCKET,      TEST_METHOD(Messages80Test::test_stat_bucket_message));
    putTest(DocumentProtocol::MESSAGE_UPDATEDOCUMENT,  TEST_METHOD(Messages80Test::test_update_document_message));
    putTest(DocumentProtocol::MESSAGE_VISITORINFO,     TEST_METHOD(Messages80Test::test_visitor_info_message));

    putTest(DocumentProtocol::REPLY_CREATEVISITOR,     TEST_METHOD(Messages80Test::test_create_visitor_reply));
    putTest(DocumentProtocol::REPLY_DESTROYVISITOR,    TEST_METHOD(Messages80Test::test_destroy_visitor_reply));
    putTest(DocumentProtocol::REPLY_DOCUMENTIGNORED,   TEST_METHOD(Messages80Test::test_document_ignored_reply));
    putTest(DocumentProtocol::REPLY_DOCUMENTLIST,      TEST_METHOD(Messages80Test::test_document_list_reply));
    putTest(DocumentProtocol::REPLY_EMPTYBUCKETS,      TEST_METHOD(Messages80Test::test_empty_buckets_reply));
    putTest(DocumentProtocol::REPLY_GETBUCKETLIST,     TEST_METHOD(Messages80Test::test_get_bucket_list_reply));
    putTest(DocumentProtocol::REPLY_GETBUCKETSTATE,    TEST_METHOD(Messages80Test::test_get_bucket_state_reply));
    putTest(DocumentProtocol::REPLY_GETDOCUMENT,       TEST_METHOD(Messages80Test::test_get_document_reply));
    putTest(DocumentProtocol::REPLY_MAPVISITOR,        TEST_METHOD(Messages80Test::test_map_visitor_reply));
    putTest(DocumentProtocol::REPLY_PUTDOCUMENT,       TEST_METHOD(Messages80Test::test_put_document_reply));
    putTest(DocumentProtocol::REPLY_QUERYRESULT,       TEST_METHOD(Messages80Test::test_query_result_reply));
    putTest(DocumentProtocol::REPLY_REMOVEDOCUMENT,    TEST_METHOD(Messages80Test::test_remove_document_reply));
    putTest(DocumentProtocol::REPLY_REMOVELOCATION,    TEST_METHOD(Messages80Test::test_remove_location_reply));
    putTest(DocumentProtocol::REPLY_STATBUCKET,        TEST_METHOD(Messages80Test::test_stat_bucket_reply));
    putTest(DocumentProtocol::REPLY_UPDATEDOCUMENT,    TEST_METHOD(Messages80Test::test_update_document_reply));
    putTest(DocumentProtocol::REPLY_VISITORINFO,       TEST_METHOD(Messages80Test::test_visitor_info_reply));
    putTest(DocumentProtocol::REPLY_WRONGDISTRIBUTION, TEST_METHOD(Messages80Test::test_wrong_distribution_reply));
}

namespace {

document::Document::SP
createDoc(const DocumentTypeRepo& repo, const string& type_name, const string& id) {
    return std::make_shared<document::Document>(repo, *repo.getDocumentType(type_name), document::DocumentId(id));
}

}

bool Messages80Test::test_get_document_message() {
    GetDocumentMessage tmp(document::DocumentId("id:ns:testdoc::"), "foo bar");
    EXPECT_EQUAL(280u, sizeof(GetDocumentMessage)); // FIXME doesn't belong here
    serialize("GetDocumentMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("GetDocumentMessage", DocumentProtocol::MESSAGE_GETDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<GetDocumentMessage&>(*obj);
            EXPECT_EQUAL(ref.getDocumentId().toString(), "id:ns:testdoc::");
            EXPECT_EQUAL(ref.getFieldSet(), "foo bar");
        }
    }
    return true;
}

void Messages80Test::do_test_get_reply_with_doc() {
    auto doc = createDoc(getTypeRepo(), "testdoc", "id:ns:testdoc::");
    GetDocumentReply tmp(doc);
    tmp.setLastModified(1234567);

    EXPECT_EQUAL(128u, sizeof(GetDocumentReply)); // FIXME doesn't belong here!
    serialize("GetDocumentReply", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("GetDocumentReply", DocumentProtocol::REPLY_GETDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<GetDocumentReply&>(*obj);
            EXPECT_EQUAL(ref.getLastModified(), 1234567ULL); // FIXME signed vs. unsigned... -_-
            ASSERT_TRUE(ref.hasDocument());
            auto& doc2 = ref.getDocument();
            EXPECT_EQUAL(doc2.getType().getName(), "testdoc");
            EXPECT_EQUAL(doc2.getId().toString(), "id:ns:testdoc::");
            EXPECT_EQUAL(doc2.getLastModified(), 1234567LL); // FIXME signed vs. unsigned... -_-
        }
    }
}

void Messages80Test::do_test_empty_get_reply() {
    GetDocumentReply tmp;
    serialize("GetDocumentReply-empty", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("GetDocumentReply-empty", DocumentProtocol::REPLY_GETDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<GetDocumentReply&>(*obj);
            EXPECT_EQUAL(ref.getLastModified(), 0ULL);
            EXPECT_FALSE(ref.hasDocument());
        }
    }
}

bool Messages80Test::test_get_document_reply() {
    TEST_DO(do_test_get_reply_with_doc());
    TEST_DO(do_test_empty_get_reply());
    return true;
}

bool Messages80Test::test_put_document_message() {
    auto doc = createDoc(getTypeRepo(), "testdoc", "id:ns:testdoc::");
    PutDocumentMessage msg(doc);

    msg.setTimestamp(666);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    // FIXME these don't belong here!
    EXPECT_EQUAL(64u, sizeof(vespalib::string));
    EXPECT_EQUAL(sizeof(vespalib::string), sizeof(TestAndSetCondition));
    EXPECT_EQUAL(112u, sizeof(DocumentMessage));
    EXPECT_EQUAL(sizeof(TestAndSetCondition) + sizeof(DocumentMessage), sizeof(TestAndSetMessage));
    EXPECT_EQUAL(sizeof(TestAndSetMessage) + 32, sizeof(PutDocumentMessage));

    serialize("PutDocumentMessage", msg);

    for (auto lang : languages()) {
        auto routableUp = deserialize("PutDocumentMessage", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang);
        if (EXPECT_TRUE(routableUp)) {
            auto& deserializedMsg = dynamic_cast<PutDocumentMessage &>(*routableUp);

            EXPECT_EQUAL(deserializedMsg.getDocument().getType().getName(), msg.getDocument().getType().getName());
            EXPECT_EQUAL(deserializedMsg.getDocument().getId().toString(), msg.getDocument().getId().toString());
            EXPECT_EQUAL(deserializedMsg.getTimestamp(), msg.getTimestamp());
            EXPECT_GREATER(deserializedMsg.getApproxSize(), 0u);
            EXPECT_EQUAL(deserializedMsg.getCondition().getSelection(), msg.getCondition().getSelection());
            EXPECT_FALSE(deserializedMsg.get_create_if_non_existent());
        }
    }

    //-------------------------------------------------------------------------

    PutDocumentMessage msg2(createDoc(getTypeRepo(), "testdoc", "id:ns:testdoc::"));
    msg2.set_create_if_non_existent(true);
    serialize("PutDocumentMessage-create", msg2);
    for (auto lang : languages()) {
        auto obj = deserialize("PutDocumentMessage-create", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& decoded = dynamic_cast<PutDocumentMessage&>(*obj);
            EXPECT_TRUE(decoded.get_create_if_non_existent());
        }
    }
    return true;
}

bool Messages80Test::test_put_document_reply() {
    WriteDocumentReply reply(DocumentProtocol::REPLY_PUTDOCUMENT);
    reply.setHighestModificationTimestamp(30);

    serialize("PutDocumentReply", reply);
    EXPECT_EQUAL(sizeof(WriteDocumentReply), 112u); // FIXME doesn't belong here!

    for (auto lang : languages()) {
        auto obj = deserialize("PutDocumentReply", DocumentProtocol::REPLY_PUTDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<WriteDocumentReply&>(*obj);
            EXPECT_EQUAL(ref.getHighestModificationTimestamp(), 30u);
        }
    }
    return true;
}

bool Messages80Test::test_update_document_message() {
    const DocumentTypeRepo& repo = getTypeRepo();
    const document::DocumentType& docType = *repo.getDocumentType("testdoc");

    auto doc_update = std::make_shared<document::DocumentUpdate>(repo, docType, document::DocumentId("id:ns:testdoc::"));
    doc_update->addFieldPathUpdate(std::make_unique<document::RemoveFieldPathUpdate>("intfield", "testdoc.intfield > 0"));

    UpdateDocumentMessage msg(std::move(doc_update));
    msg.setOldTimestamp(666u);
    msg.setNewTimestamp(777u);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQUAL(sizeof(TestAndSetMessage) + 32, sizeof(UpdateDocumentMessage)); // FIXME doesn't belong here!
    serialize("UpdateDocumentMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("UpdateDocumentMessage", DocumentProtocol::MESSAGE_UPDATEDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& decoded = dynamic_cast<UpdateDocumentMessage&>(*obj);
            EXPECT_EQUAL(decoded.getDocumentUpdate(), msg.getDocumentUpdate());
            EXPECT_EQUAL(decoded.getOldTimestamp(), msg.getOldTimestamp());
            EXPECT_EQUAL(decoded.getNewTimestamp(), msg.getNewTimestamp());
            EXPECT_GREATER(decoded.getApproxSize(), 0u); // Actual value depends on protobuf size
            EXPECT_EQUAL(decoded.getCondition().getSelection(), msg.getCondition().getSelection());
        }
    }
    return true;
}

bool Messages80Test::test_update_document_reply() {
    UpdateDocumentReply reply;
    reply.setWasFound(true);
    reply.setHighestModificationTimestamp(30);

    serialize("UpdateDocumentReply", reply);
    EXPECT_EQUAL(120u, sizeof(UpdateDocumentReply)); // FIXME doesn't belong here!

    for (auto lang : languages()) {
        auto obj = deserialize("UpdateDocumentReply", DocumentProtocol::REPLY_UPDATEDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<UpdateDocumentReply&>(*obj);
            EXPECT_EQUAL(ref.getHighestModificationTimestamp(), 30u);
            EXPECT_TRUE(ref.wasFound());
        }
    }
    return true;
}

bool Messages80Test::test_remove_document_message() {
    RemoveDocumentMessage msg(document::DocumentId("id:ns:testdoc::"));
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQUAL(sizeof(TestAndSetMessage) + 104, sizeof(RemoveDocumentMessage)); // FIXME doesn't belong here!
    serialize("RemoveDocumentMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("RemoveDocumentMessage", DocumentProtocol::MESSAGE_REMOVEDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<RemoveDocumentMessage &>(*obj);
            EXPECT_EQUAL(ref.getDocumentId().toString(), "id:ns:testdoc::");
            EXPECT_EQUAL(ref.getCondition().getSelection(), msg.getCondition().getSelection());
        }
    }
    return true;
}

bool Messages80Test::test_remove_document_reply() {
    RemoveDocumentReply reply;
    std::vector<uint64_t> ts;
    reply.setWasFound(true);
    reply.setHighestModificationTimestamp(30);
    EXPECT_EQUAL(120u, sizeof(RemoveDocumentReply)); // FIXME doesn't belong here!

    serialize("RemoveDocumentReply", reply);

    for (auto lang : languages()) {
        auto obj = deserialize("RemoveDocumentReply", DocumentProtocol::REPLY_REMOVEDOCUMENT, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<RemoveDocumentReply&>(*obj);
            EXPECT_EQUAL(ref.getHighestModificationTimestamp(), 30u);
            EXPECT_TRUE(ref.wasFound());
        }
    }
    return true;
}

bool Messages80Test::test_remove_location_message() {
    document::BucketIdFactory factory;
    document::select::Parser parser(getTypeRepo(), factory);
    RemoveLocationMessage msg(factory, parser, "id.group == \"mygroup\"");
    msg.setBucketSpace("bjarne");
    serialize("RemoveLocationMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("RemoveLocationMessage", DocumentProtocol::MESSAGE_REMOVELOCATION, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<RemoveLocationMessage&>(*obj);
            EXPECT_EQUAL(ref.getDocumentSelection(), "id.group == \"mygroup\"");
            EXPECT_EQUAL(ref.getBucketSpace(), "bjarne");
        }
    }
    return true;
}

bool Messages80Test::test_remove_location_reply() {
    DocumentReply tmp(DocumentProtocol::REPLY_REMOVELOCATION);
    serialize("RemoveLocationReply", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("RemoveLocationReply", DocumentProtocol::REPLY_REMOVELOCATION, lang);
        EXPECT_TRUE(obj);
    }
    return true;
}

bool Messages80Test::test_create_visitor_message() {
    CreateVisitorMessage tmp("SomeLibrary", "myvisitor", "newyork", "london");
    tmp.setDocumentSelection("true and false or true");
    tmp.getParameters().set("myvar", "somevalue");
    tmp.getParameters().set("anothervar", uint64_t(34));
    tmp.getBuckets().emplace_back(16, 1234);
    tmp.setVisitRemoves(true);
    tmp.setVisitInconsistentBuckets(true);
    tmp.setFieldSet("foo bar");
    tmp.setMaxBucketsPerVisitor(2);
    tmp.setMaximumPendingReplyCount(12);
    tmp.setBucketSpace("bjarne");

    serialize("CreateVisitorMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("CreateVisitorMessage", DocumentProtocol::MESSAGE_CREATEVISITOR, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<CreateVisitorMessage&>(*obj);

            EXPECT_EQUAL(ref.getLibraryName(), "SomeLibrary");
            EXPECT_EQUAL(ref.getInstanceId(), "myvisitor");
            EXPECT_EQUAL(ref.getControlDestination(), "newyork");
            EXPECT_EQUAL(ref.getDataDestination(), "london");
            EXPECT_EQUAL(ref.getDocumentSelection(), "true and false or true");
            EXPECT_EQUAL(ref.getFieldSet(), "foo bar");
            EXPECT_EQUAL(ref.getMaximumPendingReplyCount(), uint32_t(12));
            EXPECT_TRUE(ref.visitRemoves());
            EXPECT_TRUE(ref.visitInconsistentBuckets());
            ASSERT_EQUAL(ref.getBuckets().size(), size_t(1));
            EXPECT_EQUAL(ref.getBuckets()[0], document::BucketId(16, 1234));
            EXPECT_EQUAL(ref.getParameters().get("myvar"), "somevalue");
            EXPECT_EQUAL(ref.getParameters().get("anothervar", uint64_t(1)), uint64_t(34));
            EXPECT_EQUAL(ref.getMaxBucketsPerVisitor(), uint32_t(2));
            EXPECT_EQUAL(ref.getBucketSpace(), "bjarne");
        }
    }
    return true;
}

bool Messages80Test::test_create_visitor_reply() {
    CreateVisitorReply reply(DocumentProtocol::REPLY_CREATEVISITOR);
    reply.setLastBucket(document::BucketId(16, 123));
    vdslib::VisitorStatistics vs;
    vs.setBucketsVisited(3);
    vs.setDocumentsVisited(1000);
    vs.setBytesVisited(1024000);
    vs.setDocumentsReturned(123);
    vs.setBytesReturned(512000);
    reply.setVisitorStatistics(vs);

    serialize("CreateVisitorReply", reply);

    for (auto lang : languages()) {
        auto obj = deserialize("CreateVisitorReply", DocumentProtocol::REPLY_CREATEVISITOR, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<CreateVisitorReply&>(*obj);
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

bool Messages80Test::test_destroy_visitor_message() {
    DestroyVisitorMessage tmp("myvisitor");
    serialize("DestroyVisitorMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("DestroyVisitorMessage", DocumentProtocol::MESSAGE_DESTROYVISITOR, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<DestroyVisitorMessage&>(*obj);
            EXPECT_EQUAL(ref.getInstanceId(), "myvisitor");
        }
    }
    return true;
}

bool Messages80Test::test_destroy_visitor_reply() {
    return try_visitor_reply("DestroyVisitorReply", DocumentProtocol::REPLY_DESTROYVISITOR);
}

bool Messages80Test::test_map_visitor_message() {
    MapVisitorMessage tmp;
    tmp.getData().set("foo", 3);
    tmp.getData().set("bar", 5);

    serialize("MapVisitorMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("MapVisitorMessage", DocumentProtocol::MESSAGE_MAPVISITOR, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<MapVisitorMessage&>(*obj);
            EXPECT_EQUAL(ref.getData().size(), 2u);
            EXPECT_EQUAL(ref.getData().get("foo", 0), 3);
            EXPECT_EQUAL(ref.getData().get("bar", 0), 5);
        }
    }
    return true;
}

bool Messages80Test::test_map_visitor_reply() {
    return try_visitor_reply("MapVisitorReply", DocumentProtocol::REPLY_MAPVISITOR);
}

bool Messages80Test::test_query_result_message() {
    QueryResultMessage srm;
    vdslib::SearchResult& sr(srm.getSearchResult());
    EXPECT_EQUAL(srm.getSequenceId(), 0u);
    EXPECT_EQUAL(sr.getHitCount(), 0u);
    EXPECT_EQUAL(sr.getAggregatorList().getSerializedSize(), 4u);
    EXPECT_EQUAL(sr.getSerializedSize(), 20u);
    EXPECT_EQUAL(srm.getApproxSize(), 28u);

    serialize("QueryResultMessage-1", srm);

    // Serialization is only implemented in C++
    {
        auto routable = deserialize("QueryResultMessage-1", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        if (!EXPECT_TRUE(routable)) {
            return false;
        }
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        vdslib::SearchResult& dr = dm.getSearchResult();
        EXPECT_EQUAL(dm.getSequenceId(), size_t(0));
        EXPECT_EQUAL(dr.getHitCount(), size_t(0));
    }

    sr.addHit(0, "doc1", 89);
    sr.addHit(1, "doc17", 109);
    serialize("QueryResultMessage-2", srm);

    const char* doc_id;
    vdslib::SearchResult::RankType rank;

    {
        auto routable = deserialize("QueryResultMessage-2", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        if (!EXPECT_TRUE(routable)) {
            return false;
        }
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQUAL(dr.getHitCount(), size_t(2));
        dr.getHit(0, doc_id, rank);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
        EXPECT_EQUAL(strcmp("doc1", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
        EXPECT_EQUAL(strcmp("doc17", doc_id), 0);
    }

    sr.sort();
    serialize("QueryResultMessage-3", srm);

    {
        auto routable = deserialize("QueryResultMessage-3", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        if (!EXPECT_TRUE(routable)) {
            return false;
        }
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQUAL(dr.getHitCount(), size_t(2));
        dr.getHit(0, doc_id, rank);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
        EXPECT_EQUAL(strcmp("doc17", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
        EXPECT_EQUAL(strcmp("doc1", doc_id), 0);
    }

    QueryResultMessage srm2;
    vdslib::SearchResult& sr2(srm2.getSearchResult());
    sr2.addHit(0, "doc1", 89, "sortdata2", 9);
    sr2.addHit(1, "doc17", 109, "sortdata1", 9);
    sr2.addHit(2, "doc18", 90, "sortdata3", 9);
    serialize("QueryResultMessage-4", srm2);

    {
        auto routable = deserialize("QueryResultMessage-4", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        if (!EXPECT_TRUE(routable)) {
            return false;
        }
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQUAL(dr.getHitCount(), size_t(3));
        dr.getHit(0, doc_id, rank);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
        EXPECT_EQUAL(strcmp("doc1", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
        EXPECT_EQUAL(strcmp("doc17", doc_id), 0);
        dr.getHit(2, doc_id, rank);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(90));
        EXPECT_EQUAL(strcmp("doc18", doc_id), 0);
    }

    sr2.sort();
    const void* buf;
    size_t sz;
    sr2.getHit(0, doc_id, rank);
    sr2.getSortBlob(0, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata1", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQUAL(strcmp("doc17", doc_id), 0);
    sr2.getHit(1, doc_id, rank);
    sr2.getSortBlob(1, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata2", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQUAL(strcmp("doc1", doc_id), 0);
    sr2.getHit(2, doc_id, rank);
    sr2.getSortBlob(2, buf, sz);
    EXPECT_EQUAL(sz, 9u);
    EXPECT_EQUAL(memcmp("sortdata3", buf, sz), 0);
    EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(90));
    EXPECT_EQUAL(strcmp("doc18", doc_id), 0);

    serialize("QueryResultMessage-5", srm2);
    {
        auto routable = deserialize("QueryResultMessage-5", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        if (!EXPECT_TRUE(routable)) {
            return false;
        }
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQUAL(dr.getHitCount(), size_t(3));
        dr.getHit(0, doc_id, rank);
        dr.getSortBlob(0, buf, sz);
        EXPECT_EQUAL(sz, 9u);
        EXPECT_EQUAL(memcmp("sortdata1", buf, sz), 0);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(109));
        EXPECT_EQUAL(strcmp("doc17", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        dr.getSortBlob(1, buf, sz);
        EXPECT_EQUAL(sz, 9u);
        EXPECT_EQUAL(memcmp("sortdata2", buf, sz), 0);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(89));
        EXPECT_EQUAL(strcmp("doc1", doc_id), 0);
        dr.getHit(2, doc_id, rank);
        dr.getSortBlob(2, buf, sz);
        EXPECT_EQUAL(sz, 9u);
        EXPECT_EQUAL(memcmp("sortdata3", buf, sz), 0);
        EXPECT_EQUAL(rank, vdslib::SearchResult::RankType(90));
        EXPECT_EQUAL(strcmp("doc18", doc_id), 0);
    }

    QueryResultMessage qrm3;
    auto& sr3 = qrm3.getSearchResult();
    sr3.addHit(0, "doc1", 5);
    sr3.addHit(1, "doc2", 7);
    FeatureValues mf;
    mf.names.emplace_back("foo");
    mf.names.emplace_back("bar");
    mf.values.resize(4);
    mf.values[0].set_double(1.0);
    mf.values[1].set_data({doc1_mf_data.data(), doc1_mf_data.size()});
    mf.values[2].set_double(12.0);
    mf.values[3].set_data({doc2_mf_data.data(), doc2_mf_data.size()});
    sr3.set_match_features(FeatureValues(mf));
    sr3.sort();

    serialize("QueryResultMessage-6", qrm3);
    {
        auto routable = deserialize("QueryResultMessage-6", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        if (!EXPECT_TRUE(routable)) {
            return false;
        }
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQUAL(dr.getHitCount(), size_t(2));
        dr.getHit(0, doc_id, rank);
        EXPECT_EQUAL(vdslib::SearchResult::RankType(7), rank);
        EXPECT_EQUAL(strcmp("doc2", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        EXPECT_EQUAL(vdslib::SearchResult::RankType(5), rank);
        EXPECT_EQUAL(strcmp("doc1", doc_id), 0);
        auto mfv = dr.get_match_feature_values(0);
        EXPECT_EQUAL(mfv.size(), 2u);
        EXPECT_EQUAL(mfv[0].as_double(), 12.0);
        EXPECT_EQUAL(mfv[1].as_data().make_string(), "There");
        mfv = dr.get_match_feature_values(1);
        EXPECT_EQUAL(mfv.size(), 2u);
        EXPECT_EQUAL(mfv[0].as_double(), 1.0);
        EXPECT_EQUAL(mfv[1].as_data().make_string(), "Hi");
        const auto& mf_names = dr.get_match_features().names;
        EXPECT_EQUAL(mf_names.size(), 2u);
        EXPECT_EQUAL(mf_names[0], "foo");
        EXPECT_EQUAL(mf_names[1], "bar");
    }
    return true;
}

bool Messages80Test::test_query_result_reply() {
    return try_visitor_reply("QueryResultReply", DocumentProtocol::REPLY_QUERYRESULT);
}

bool Messages80Test::test_visitor_info_message() {
    VisitorInfoMessage tmp;
    tmp.getFinishedBuckets().emplace_back(16, 1);
    tmp.getFinishedBuckets().emplace_back(16, 2);
    tmp.getFinishedBuckets().emplace_back(16, 4);
    string utf8 = "error message: \u00e6\u00c6\u00f8\u00d8\u00e5\u00c5\u00f6\u00d6"; // FIXME utf-8 literal
    tmp.setErrorMessage(utf8);

    serialize("VisitorInfoMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("VisitorInfoMessage", DocumentProtocol::MESSAGE_VISITORINFO, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<VisitorInfoMessage&>(*obj);
            ASSERT_EQUAL(ref.getFinishedBuckets().size(), 3u);
            EXPECT_EQUAL(ref.getFinishedBuckets()[0], document::BucketId(16, 1));
            EXPECT_EQUAL(ref.getFinishedBuckets()[1], document::BucketId(16, 2));
            EXPECT_EQUAL(ref.getFinishedBuckets()[2], document::BucketId(16, 4));
            EXPECT_EQUAL(ref.getErrorMessage(), utf8);
        }
    }
    return true;
}

bool Messages80Test::test_visitor_info_reply() {
    return try_visitor_reply("VisitorInfoReply", DocumentProtocol::REPLY_VISITORINFO);
}

bool Messages80Test::test_document_list_message() {
    auto doc = createDoc(getTypeRepo(), "testdoc", "id:scheme:testdoc:n=1234:1");
    DocumentListMessage::Entry entry(1234, std::move(doc), true);
    DocumentListMessage tmp(document::BucketId(17, 1234));
    tmp.getDocuments().push_back(std::move(entry));

    serialize("DocumentListMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("DocumentListMessage", DocumentProtocol::MESSAGE_DOCUMENTLIST, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<DocumentListMessage&>(*obj);
            ASSERT_EQUAL(ref.getDocuments().size(), 1u);
            EXPECT_EQUAL(ref.getDocuments()[0].getDocument()->getId().toString(), "id:scheme:testdoc:n=1234:1");
            EXPECT_EQUAL(ref.getDocuments()[0].getTimestamp(), 1234);
            EXPECT_TRUE(ref.getDocuments()[0].isRemoveEntry());
        }
    }
    return true;
}

bool Messages80Test::test_document_list_reply() {
    return try_visitor_reply("DocumentListReply", DocumentProtocol::REPLY_DOCUMENTLIST);
}

bool Messages80Test::test_empty_buckets_message() {
    std::vector<document::BucketId> bids;
    for (size_t i=0; i < 13; ++i) {
        bids.emplace_back(16, i);
    }
    EmptyBucketsMessage msg(bids);

    serialize("EmptyBucketsMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("EmptyBucketsMessage", DocumentProtocol::MESSAGE_EMPTYBUCKETS, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<EmptyBucketsMessage&>(*obj);
            ASSERT_EQUAL(ref.getBucketIds().size(), 13u);
            for (size_t i = 0; i < 13; ++i) {
                EXPECT_EQUAL(ref.getBucketIds()[i], document::BucketId(16, i));
            }
        }
    }
    return true;
}

bool Messages80Test::test_empty_buckets_reply() {
    return try_visitor_reply("EmptyBucketsReply", DocumentProtocol::REPLY_EMPTYBUCKETS);
}

bool Messages80Test::test_get_bucket_list_message() {
    GetBucketListMessage msg(document::BucketId(16, 123));
    msg.setBucketSpace("beartato");

    serialize("GetBucketListMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("GetBucketListMessage", DocumentProtocol::MESSAGE_GETBUCKETLIST, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<GetBucketListMessage&>(*obj);
            EXPECT_EQUAL(ref.getBucketId(), document::BucketId(16, 123));
            EXPECT_EQUAL(ref.getBucketSpace(), "beartato");
        }
    }
    return true;
}

bool Messages80Test::test_get_bucket_list_reply() {
    GetBucketListReply reply;
    reply.getBuckets().emplace_back(document::BucketId(16, 123), "foo");
    reply.getBuckets().emplace_back(document::BucketId(17, 1123), "bar");
    reply.getBuckets().emplace_back(document::BucketId(18, 11123), "zoink");

    serialize("GetBucketListReply", reply);

    for (auto lang : languages()) {
        auto obj = deserialize("GetBucketListReply", DocumentProtocol::REPLY_GETBUCKETLIST, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<GetBucketListReply&>(*obj);
            ASSERT_EQUAL(ref.getBuckets().size(), 3u);
            EXPECT_EQUAL(ref.getBuckets()[0], GetBucketListReply::BucketInfo(document::BucketId(16, 123), "foo"));
            EXPECT_EQUAL(ref.getBuckets()[1], GetBucketListReply::BucketInfo(document::BucketId(17, 1123), "bar"));
            EXPECT_EQUAL(ref.getBuckets()[2], GetBucketListReply::BucketInfo(document::BucketId(18, 11123), "zoink"));
        }
    }
    return true;
}

bool Messages80Test::test_get_bucket_state_message() {
    GetBucketStateMessage tmp;
    tmp.setBucketId(document::BucketId(16, 666));

    serialize("GetBucketStateMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("GetBucketStateMessage", DocumentProtocol::MESSAGE_GETBUCKETSTATE, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<GetBucketStateMessage&>(*obj);
            EXPECT_EQUAL(ref.getBucketId().getUsedBits(), 16u);
            EXPECT_EQUAL(ref.getBucketId().getId(), 4611686018427388570ULL);
        }
    }
    return true;
}

bool Messages80Test::test_get_bucket_state_reply() {
    auto foo = document::DocumentId("id:ns:testdoc::foo").getGlobalId();
    auto bar = document::DocumentId("id:ns:testdoc::bar").getGlobalId();
    auto baz = document::DocumentId("id:ns:testdoc::baz");

    GetBucketStateReply reply;
    reply.getBucketState().emplace_back(foo, 777, false);
    reply.getBucketState().emplace_back(bar, 888, true);
    reply.getBucketState().emplace_back(baz, 999, false);
    serialize("GetBucketStateReply", reply);

    for (auto lang : languages()) {
        auto obj = deserialize("GetBucketStateReply", DocumentProtocol::REPLY_GETBUCKETSTATE, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<GetBucketStateReply&>(*obj);
            ASSERT_EQUAL(ref.getBucketState().size(), 3u);
            EXPECT_EQUAL(ref.getBucketState()[0].getTimestamp(), 777u);
            EXPECT_FALSE(ref.getBucketState()[0].getDocumentId());
            EXPECT_EQUAL(ref.getBucketState()[0].getGlobalId(), foo);
            EXPECT_FALSE(ref.getBucketState()[0].isRemoveEntry());

            EXPECT_EQUAL(ref.getBucketState()[1].getTimestamp(), 888u);
            EXPECT_FALSE(ref.getBucketState()[1].getDocumentId());
            EXPECT_EQUAL(ref.getBucketState()[1].getGlobalId(), bar);
            EXPECT_TRUE(ref.getBucketState()[1].isRemoveEntry());

            EXPECT_EQUAL(ref.getBucketState()[2].getTimestamp(), 999u);
            EXPECT_EQUAL(ref.getBucketState()[2].getGlobalId(), baz.getGlobalId());
            EXPECT_FALSE(ref.getBucketState()[2].isRemoveEntry());
            ASSERT_TRUE(ref.getBucketState()[2].getDocumentId());
            EXPECT_EQUAL(*ref.getBucketState()[2].getDocumentId(), baz);
        }
    }
    return true;
}

bool Messages80Test::test_stat_bucket_message() {
    StatBucketMessage msg(document::BucketId(16, 123), "id.user=123");
    msg.setBucketSpace("andrei");

    serialize("StatBucketMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("StatBucketMessage", DocumentProtocol::MESSAGE_STATBUCKET, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<StatBucketMessage&>(*obj);
            EXPECT_EQUAL(ref.getBucketId(), document::BucketId(16, 123));
            EXPECT_EQUAL(ref.getDocumentSelection(), "id.user=123");
            EXPECT_EQUAL(ref.getBucketSpace(), "andrei");
        }
    }
    return true;
}

bool Messages80Test::test_stat_bucket_reply() {
    StatBucketReply msg;
    msg.setResults("These are the votes of the Norwegian jury");

    serialize("StatBucketReply", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("StatBucketReply", DocumentProtocol::REPLY_STATBUCKET, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<StatBucketReply&>(*obj);
            EXPECT_EQUAL(ref.getResults(), "These are the votes of the Norwegian jury");
        }
    }
    return true;
}

bool Messages80Test::test_wrong_distribution_reply() {
    WrongDistributionReply tmp("distributor:3 storage:2");

    serialize("WrongDistributionReply", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("WrongDistributionReply", DocumentProtocol::REPLY_WRONGDISTRIBUTION, lang);
        if (EXPECT_TRUE(obj)) {
            auto& ref = dynamic_cast<WrongDistributionReply&>(*obj);
            EXPECT_EQUAL(ref.getSystemState(), "distributor:3 storage:2");
        }
    }
    return true;
}

bool Messages80Test::test_document_ignored_reply() {
    DocumentIgnoredReply tmp;
    serialize("DocumentIgnoredReply", tmp);
    for (auto lang : languages()) {
        auto obj = deserialize("DocumentIgnoredReply", DocumentProtocol::REPLY_DOCUMENTIGNORED, lang);
        EXPECT_TRUE(obj);
    }
    return true;
}

bool Messages80Test::try_visitor_reply(const string& filename, uint32_t type) {
    VisitorReply tmp(type);
    serialize(filename, tmp);

    for (auto lang : languages()) {
        auto obj = deserialize(filename, type, lang);
        if (EXPECT_TRUE(obj)) {
            auto* ptr = dynamic_cast<VisitorReply*>(obj.get());
            EXPECT_TRUE(ptr);
        }
    }
    return true;
}

// TODO rewrite to Gtest
TEST_APPHOOK(Messages80Test);
