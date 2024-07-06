// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "message_fixture.h"
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

namespace documentapi {

// This is not version-dependent
TEST(MessagesTest, concrete_types_have_expected_sizes) {
    EXPECT_EQ(sizeof(GetDocumentMessage),    152u + 2 *sizeof(vespalib::string));
    EXPECT_EQ(sizeof(GetDocumentReply),      128u);
    EXPECT_EQ(sizeof(TestAndSetCondition),   sizeof(vespalib::string));
    EXPECT_EQ(sizeof(DocumentMessage),       112u);
    EXPECT_EQ(sizeof(TestAndSetMessage),     sizeof(TestAndSetCondition) + sizeof(DocumentMessage));
    EXPECT_EQ(sizeof(PutDocumentMessage),    sizeof(TestAndSetMessage) + 32);
    EXPECT_EQ(sizeof(WriteDocumentReply),    112u);
    EXPECT_EQ(sizeof(UpdateDocumentReply),   120u);
    EXPECT_EQ(sizeof(UpdateDocumentMessage), sizeof(TestAndSetMessage) + 40);
    EXPECT_EQ(sizeof(RemoveDocumentMessage), sizeof(TestAndSetMessage) + 40 + sizeof(vespalib::string));
    EXPECT_EQ(sizeof(RemoveDocumentReply),   120u);
}

struct Messages80Test : MessageFixture {
    [[nodiscard]] vespalib::Version tested_protocol_version() const override {
        // Must be as high--or higher--than the v8 protocol version specified in documentprocotol.cpp
        // (and equal to its corresponding value in the Java implementation).
        return {8, 310};
    }

    void try_visitor_reply(const std::string& filename, uint32_t type);

    void check_update_create_flag(uint32_t lang, const std::string& name, bool expected_create, bool expected_cached) {
        auto obj = deserialize(name, DocumentProtocol::MESSAGE_UPDATEDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& msg = dynamic_cast<UpdateDocumentMessage&>(*obj);
        EXPECT_EQ(msg.has_cached_create_if_missing(), expected_cached);
        EXPECT_EQ(msg.create_if_missing(), expected_create);
    };
};

namespace {

std::vector<char> doc1_mf_data{'H', 'i'};
std::vector<char> doc2_mf_data{'T', 'h', 'e', 'r', 'e'};

}

namespace {

document::Document::SP
createDoc(const DocumentTypeRepo& repo, const string& type_name, const string& id) {
    return std::make_shared<document::Document>(repo, *repo.getDocumentType(type_name), document::DocumentId(id));
}

}

TEST_F(Messages80Test, get_document_message) {
    GetDocumentMessage tmp(document::DocumentId("id:ns:testdoc::"), "foo bar");
    serialize("GetDocumentMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("GetDocumentMessage", DocumentProtocol::MESSAGE_GETDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetDocumentMessage&>(*obj);
        EXPECT_EQ(ref.getDocumentId().toString(), "id:ns:testdoc::");
        EXPECT_EQ(ref.getFieldSet(), "foo bar");
    }
}

TEST_F(Messages80Test, get_reply_with_doc) {
    auto doc = createDoc(type_repo(), "testdoc", "id:ns:testdoc::");
    GetDocumentReply tmp(doc);
    tmp.setLastModified(1234567);

    serialize("GetDocumentReply", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("GetDocumentReply", DocumentProtocol::REPLY_GETDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetDocumentReply&>(*obj);
        EXPECT_EQ(ref.getLastModified(), 1234567ULL); // FIXME signed vs. unsigned... -_-
        ASSERT_TRUE(ref.hasDocument());
        auto& doc2 = ref.getDocument();
        EXPECT_EQ(doc2.getType().getName(), "testdoc");
        EXPECT_EQ(doc2.getId().toString(), "id:ns:testdoc::");
        EXPECT_EQ(doc2.getLastModified(), 1234567LL); // FIXME signed vs. unsigned... -_-
    }
}

TEST_F(Messages80Test, empty_get_reply) {
    GetDocumentReply tmp;
    serialize("GetDocumentReply-empty", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("GetDocumentReply-empty", DocumentProtocol::REPLY_GETDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetDocumentReply&>(*obj);
        EXPECT_EQ(ref.getLastModified(), 0ULL);
        EXPECT_FALSE(ref.hasDocument());
    }
}

TEST_F(Messages80Test, put_document_message) {
    auto doc = createDoc(type_repo(), "testdoc", "id:ns:testdoc::");
    PutDocumentMessage msg(doc);

    msg.setTimestamp(666);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    serialize("PutDocumentMessage", msg);

    for (auto lang : languages()) {
        auto routableUp = deserialize("PutDocumentMessage", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang);
        ASSERT_TRUE(routableUp);
        auto& deserializedMsg = dynamic_cast<PutDocumentMessage &>(*routableUp);
        EXPECT_EQ(deserializedMsg.getDocument().getType().getName(), msg.getDocument().getType().getName());
        EXPECT_EQ(deserializedMsg.getDocument().getId().toString(), msg.getDocument().getId().toString());
        EXPECT_EQ(deserializedMsg.getTimestamp(), msg.getTimestamp());
        EXPECT_GT(deserializedMsg.getApproxSize(), 0u);
        EXPECT_EQ(deserializedMsg.getCondition().getSelection(), msg.getCondition().getSelection());
        EXPECT_FALSE(deserializedMsg.get_create_if_non_existent());
    }

    //-------------------------------------------------------------------------

    PutDocumentMessage msg2(createDoc(type_repo(), "testdoc", "id:ns:testdoc::"));
    msg2.set_create_if_non_existent(true);
    serialize("PutDocumentMessage-create", msg2);
    for (auto lang : languages()) {
        auto obj = deserialize("PutDocumentMessage-create", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& decoded = dynamic_cast<PutDocumentMessage&>(*obj);
        EXPECT_TRUE(decoded.get_create_if_non_existent());
    }
}

TEST_F(Messages80Test, put_document_reply) {
    WriteDocumentReply reply(DocumentProtocol::REPLY_PUTDOCUMENT);
    reply.setHighestModificationTimestamp(30);

    serialize("PutDocumentReply", reply);

    for (auto lang : languages()) {
        auto obj = deserialize("PutDocumentReply", DocumentProtocol::REPLY_PUTDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<WriteDocumentReply&>(*obj);
        EXPECT_EQ(ref.getHighestModificationTimestamp(), 30u);
    }
}

TEST_F(Messages80Test, update_document_message) {
    const DocumentTypeRepo& repo = type_repo();
    const document::DocumentType& docType = *repo.getDocumentType("testdoc");

    auto doc_update = std::make_shared<document::DocumentUpdate>(repo, docType, document::DocumentId("id:ns:testdoc::"));
    doc_update->addFieldPathUpdate(std::make_unique<document::RemoveFieldPathUpdate>("intfield", "testdoc.intfield > 0"));

    UpdateDocumentMessage msg(std::move(doc_update));
    msg.setOldTimestamp(666u);
    msg.setNewTimestamp(777u);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    serialize("UpdateDocumentMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("UpdateDocumentMessage", DocumentProtocol::MESSAGE_UPDATEDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& decoded = dynamic_cast<UpdateDocumentMessage&>(*obj);
        EXPECT_EQ(decoded.getDocumentUpdate(), msg.getDocumentUpdate());
        EXPECT_EQ(decoded.getOldTimestamp(), msg.getOldTimestamp());
        EXPECT_EQ(decoded.getNewTimestamp(), msg.getNewTimestamp());
        EXPECT_GT(decoded.getApproxSize(), 0u); // Actual value depends on protobuf size
        EXPECT_EQ(decoded.getCondition().getSelection(), msg.getCondition().getSelection());
    }
}

TEST_F(Messages80Test, update_create_if_missing_flag_can_be_read_from_legacy_update_propagation) {
    // Legacy binary files were created _prior_ to the create_if_missing flag being
    // written as part of the serialization process.
    for (auto lang : languages()) {
        check_update_create_flag(lang, "UpdateDocumentMessage-legacy-no-create-if-missing",   false, false);
        check_update_create_flag(lang, "UpdateDocumentMessage-legacy-with-create-if-missing", true,  false);
    }
}

TEST_F(Messages80Test, update_create_if_missing_flag_is_propagated) {
    const DocumentTypeRepo& repo = type_repo();
    const document::DocumentType& docType = *repo.getDocumentType("testdoc");

    auto make_update_msg = [&](bool create_if_missing, bool cache_flag) {
        auto doc_update = std::make_shared<document::DocumentUpdate>(repo, docType, document::DocumentId("id:ns:testdoc::"));
        doc_update->addFieldPathUpdate(std::make_unique<document::RemoveFieldPathUpdate>("intfield", "testdoc.intfield > 0"));
        doc_update->setCreateIfNonExistent(create_if_missing);
        auto msg = std::make_shared<UpdateDocumentMessage>(std::move(doc_update));
        msg->setOldTimestamp(666u);
        msg->setNewTimestamp(777u);
        msg->setCondition(TestAndSetCondition("There's just one condition"));
        if (cache_flag) {
            msg->set_cached_create_if_missing(create_if_missing);
        }
        return msg;
    };

    serialize("UpdateDocumentMessage-no-create-if-missing",   *make_update_msg(false, true));
    serialize("UpdateDocumentMessage-with-create-if-missing", *make_update_msg(true,  true));

    for (auto lang : languages()) {
        check_update_create_flag(lang, "UpdateDocumentMessage-no-create-if-missing",   false, true);
        check_update_create_flag(lang, "UpdateDocumentMessage-with-create-if-missing", true,  true);
    }
    // The Java protocol implementation always serializes with a cached create-flag,
    // but the C++ side does it conditionally. So these files are only checked for C++.
    serialize("UpdateDocumentMessage-no-create-if-missing-uncached",   *make_update_msg(false, false));
    serialize("UpdateDocumentMessage-with-create-if-missing-uncached", *make_update_msg(true,  false));
    check_update_create_flag(LANG_CPP, "UpdateDocumentMessage-no-create-if-missing-uncached",   false, false);
    check_update_create_flag(LANG_CPP, "UpdateDocumentMessage-with-create-if-missing-uncached", true,  false);
}

TEST_F(Messages80Test, update_document_reply) {
    UpdateDocumentReply reply;
    reply.setWasFound(true);
    reply.setHighestModificationTimestamp(30);

    serialize("UpdateDocumentReply", reply);

    for (auto lang : languages()) {
        auto obj = deserialize("UpdateDocumentReply", DocumentProtocol::REPLY_UPDATEDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<UpdateDocumentReply&>(*obj);
        EXPECT_EQ(ref.getHighestModificationTimestamp(), 30u);
        EXPECT_TRUE(ref.wasFound());
    }
}

TEST_F(Messages80Test, remove_document_message) {
    RemoveDocumentMessage msg(document::DocumentId("id:ns:testdoc::"));
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    serialize("RemoveDocumentMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("RemoveDocumentMessage", DocumentProtocol::MESSAGE_REMOVEDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<RemoveDocumentMessage &>(*obj);
        EXPECT_EQ(ref.getDocumentId().toString(), "id:ns:testdoc::");
        EXPECT_EQ(ref.getCondition().getSelection(), msg.getCondition().getSelection());
    }
}

TEST_F(Messages80Test, remove_document_reply) {
    RemoveDocumentReply reply;
    reply.setWasFound(true);
    reply.setHighestModificationTimestamp(30);

    serialize("RemoveDocumentReply", reply);

    for (auto lang : languages()) {
        auto obj = deserialize("RemoveDocumentReply", DocumentProtocol::REPLY_REMOVEDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<RemoveDocumentReply&>(*obj);
        EXPECT_EQ(ref.getHighestModificationTimestamp(), 30u);
        EXPECT_TRUE(ref.wasFound());
    }
}

TEST_F(Messages80Test, remove_location_message) {
    document::BucketIdFactory factory;
    document::select::Parser parser(type_repo(), factory);
    RemoveLocationMessage msg(factory, parser, "id.group == \"mygroup\"");
    msg.setBucketSpace("bjarne");
    serialize("RemoveLocationMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("RemoveLocationMessage", DocumentProtocol::MESSAGE_REMOVELOCATION, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<RemoveLocationMessage&>(*obj);
        EXPECT_EQ(ref.getDocumentSelection(), "id.group == \"mygroup\"");
        EXPECT_EQ(ref.getBucketSpace(), "bjarne");
    }
}

TEST_F(Messages80Test, remove_location_reply) {
    DocumentReply tmp(DocumentProtocol::REPLY_REMOVELOCATION);
    serialize("RemoveLocationReply", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("RemoveLocationReply", DocumentProtocol::REPLY_REMOVELOCATION, lang);
        EXPECT_TRUE(obj);
    }
}

TEST_F(Messages80Test, create_visitor_message) {
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
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<CreateVisitorMessage&>(*obj);

        EXPECT_EQ(ref.getLibraryName(), "SomeLibrary");
        EXPECT_EQ(ref.getInstanceId(), "myvisitor");
        EXPECT_EQ(ref.getControlDestination(), "newyork");
        EXPECT_EQ(ref.getDataDestination(), "london");
        EXPECT_EQ(ref.getDocumentSelection(), "true and false or true");
        EXPECT_EQ(ref.getFieldSet(), "foo bar");
        EXPECT_EQ(ref.getMaximumPendingReplyCount(), uint32_t(12));
        EXPECT_TRUE(ref.visitRemoves());
        EXPECT_TRUE(ref.visitInconsistentBuckets());
        ASSERT_EQ(ref.getBuckets().size(), size_t(1));
        EXPECT_EQ(ref.getBuckets()[0], document::BucketId(16, 1234));
        EXPECT_EQ(ref.getParameters().get("myvar"), "somevalue");
        EXPECT_EQ(ref.getParameters().get("anothervar", uint64_t(1)), uint64_t(34));
        EXPECT_EQ(ref.getMaxBucketsPerVisitor(), uint32_t(2));
        EXPECT_EQ(ref.getBucketSpace(), "bjarne");
    }
}

TEST_F(Messages80Test, create_visitor_reply) {
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
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<CreateVisitorReply&>(*obj);
        EXPECT_EQ(ref.getLastBucket(), document::BucketId(16, 123));
        EXPECT_EQ(ref.getVisitorStatistics().getBucketsVisited(), (uint32_t)3);
        EXPECT_EQ(ref.getVisitorStatistics().getDocumentsVisited(), (uint64_t)1000);
        EXPECT_EQ(ref.getVisitorStatistics().getBytesVisited(), (uint64_t)1024000);
        EXPECT_EQ(ref.getVisitorStatistics().getDocumentsReturned(), (uint64_t)123);
        EXPECT_EQ(ref.getVisitorStatistics().getBytesReturned(), (uint64_t)512000);
    }
}

TEST_F(Messages80Test, destroy_visitor_message) {
    DestroyVisitorMessage tmp("myvisitor");
    serialize("DestroyVisitorMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("DestroyVisitorMessage", DocumentProtocol::MESSAGE_DESTROYVISITOR, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<DestroyVisitorMessage&>(*obj);
        EXPECT_EQ(ref.getInstanceId(), "myvisitor");
    }
}

TEST_F(Messages80Test, destroy_visitor_reply) {
    ASSERT_NO_FATAL_FAILURE(try_visitor_reply("DestroyVisitorReply", DocumentProtocol::REPLY_DESTROYVISITOR));
}

TEST_F(Messages80Test, map_visitor_message) {
    MapVisitorMessage tmp;
    tmp.getData().set("foo", 3);
    tmp.getData().set("bar", 5);

    serialize("MapVisitorMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("MapVisitorMessage", DocumentProtocol::MESSAGE_MAPVISITOR, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<MapVisitorMessage&>(*obj);
        EXPECT_EQ(ref.getData().size(), 2u);
        EXPECT_EQ(ref.getData().get("foo", 0), 3);
        EXPECT_EQ(ref.getData().get("bar", 0), 5);
    }
}

TEST_F(Messages80Test, map_visitor_reply) {
    ASSERT_NO_FATAL_FAILURE(try_visitor_reply("MapVisitorReply", DocumentProtocol::REPLY_MAPVISITOR));
}

TEST_F(Messages80Test, query_result_message) {
    QueryResultMessage srm;
    vdslib::SearchResult& sr(srm.getSearchResult());
    EXPECT_EQ(srm.getSequenceId(), 0u);
    EXPECT_EQ(sr.getHitCount(), 0u);
    EXPECT_EQ(sr.getAggregatorList().getSerializedSize(), 4u);
    EXPECT_EQ(sr.getSerializedSize(), 20u);
    EXPECT_EQ(srm.getApproxSize(), 28u);

    serialize("QueryResultMessage-1", srm);

    // Serialization is only implemented in C++
    {
        auto routable = deserialize("QueryResultMessage-1", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        ASSERT_TRUE(routable);
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        vdslib::SearchResult& dr = dm.getSearchResult();
        EXPECT_EQ(dm.getSequenceId(), size_t(0));
        EXPECT_EQ(dr.getHitCount(), size_t(0));
    }

    sr.addHit(0, "doc1", 89);
    sr.addHit(1, "doc17", 109);
    serialize("QueryResultMessage-2", srm);

    const char* doc_id;
    vdslib::SearchResult::RankType rank;

    {
        auto routable = deserialize("QueryResultMessage-2", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        ASSERT_TRUE(routable);
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQ(dr.getHitCount(), size_t(2));
        dr.getHit(0, doc_id, rank);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
        EXPECT_EQ(strcmp("doc1", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
        EXPECT_EQ(strcmp("doc17", doc_id), 0);
    }

    sr.sort();
    serialize("QueryResultMessage-3", srm);

    {
        auto routable = deserialize("QueryResultMessage-3", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        ASSERT_TRUE(routable);
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQ(dr.getHitCount(), size_t(2));
        dr.getHit(0, doc_id, rank);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
        EXPECT_EQ(strcmp("doc17", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
        EXPECT_EQ(strcmp("doc1", doc_id), 0);
    }

    QueryResultMessage srm2;
    vdslib::SearchResult& sr2(srm2.getSearchResult());
    sr2.addHit(0, "doc1", 89, "sortdata2", 9);
    sr2.addHit(1, "doc17", 109, "sortdata1", 9);
    sr2.addHit(2, "doc18", 90, "sortdata3", 9);
    serialize("QueryResultMessage-4", srm2);

    {
        auto routable = deserialize("QueryResultMessage-4", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        ASSERT_TRUE(routable);
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQ(dr.getHitCount(), size_t(3));
        dr.getHit(0, doc_id, rank);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
        EXPECT_EQ(strcmp("doc1", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
        EXPECT_EQ(strcmp("doc17", doc_id), 0);
        dr.getHit(2, doc_id, rank);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(90));
        EXPECT_EQ(strcmp("doc18", doc_id), 0);
    }

    sr2.sort();
    const void* buf;
    size_t sz;
    sr2.getHit(0, doc_id, rank);
    sr2.getSortBlob(0, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata1", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQ(strcmp("doc17", doc_id), 0);
    sr2.getHit(1, doc_id, rank);
    sr2.getSortBlob(1, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata2", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQ(strcmp("doc1", doc_id), 0);
    sr2.getHit(2, doc_id, rank);
    sr2.getSortBlob(2, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata3", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(90));
    EXPECT_EQ(strcmp("doc18", doc_id), 0);

    serialize("QueryResultMessage-5", srm2);
    {
        auto routable = deserialize("QueryResultMessage-5", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
        ASSERT_TRUE(routable);
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQ(dr.getHitCount(), size_t(3));
        dr.getHit(0, doc_id, rank);
        dr.getSortBlob(0, buf, sz);
        EXPECT_EQ(sz, 9u);
        EXPECT_EQ(memcmp("sortdata1", buf, sz), 0);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
        EXPECT_EQ(strcmp("doc17", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        dr.getSortBlob(1, buf, sz);
        EXPECT_EQ(sz, 9u);
        EXPECT_EQ(memcmp("sortdata2", buf, sz), 0);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
        EXPECT_EQ(strcmp("doc1", doc_id), 0);
        dr.getHit(2, doc_id, rank);
        dr.getSortBlob(2, buf, sz);
        EXPECT_EQ(sz, 9u);
        EXPECT_EQ(memcmp("sortdata3", buf, sz), 0);
        EXPECT_EQ(rank, vdslib::SearchResult::RankType(90));
        EXPECT_EQ(strcmp("doc18", doc_id), 0);
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
        ASSERT_TRUE(routable);
        auto& dm = dynamic_cast<QueryResultMessage&>(*routable);
        auto& dr = dm.getSearchResult();
        EXPECT_EQ(dr.getHitCount(), size_t(2));
        dr.getHit(0, doc_id, rank);
        EXPECT_EQ(vdslib::SearchResult::RankType(7), rank);
        EXPECT_EQ(strcmp("doc2", doc_id), 0);
        dr.getHit(1, doc_id, rank);
        EXPECT_EQ(vdslib::SearchResult::RankType(5), rank);
        EXPECT_EQ(strcmp("doc1", doc_id), 0);
        auto mfv = dr.get_match_feature_values(0);
        EXPECT_EQ(mfv.size(), 2u);
        EXPECT_EQ(mfv[0].as_double(), 12.0);
        EXPECT_EQ(mfv[1].as_data().make_string(), "There");
        mfv = dr.get_match_feature_values(1);
        EXPECT_EQ(mfv.size(), 2u);
        EXPECT_EQ(mfv[0].as_double(), 1.0);
        EXPECT_EQ(mfv[1].as_data().make_string(), "Hi");
        const auto& mf_names = dr.get_match_features().names;
        EXPECT_EQ(mf_names.size(), 2u);
        EXPECT_EQ(mf_names[0], "foo");
        EXPECT_EQ(mf_names[1], "bar");
    }
}

TEST_F(Messages80Test, query_result_reply) {
    ASSERT_NO_FATAL_FAILURE(try_visitor_reply("QueryResultReply", DocumentProtocol::REPLY_QUERYRESULT));
}

TEST_F(Messages80Test, test_visitor_info_message) {
    VisitorInfoMessage tmp;
    tmp.getFinishedBuckets().emplace_back(16, 1);
    tmp.getFinishedBuckets().emplace_back(16, 2);
    tmp.getFinishedBuckets().emplace_back(16, 4);
    string utf8 = "error message: \u00e6\u00c6\u00f8\u00d8\u00e5\u00c5\u00f6\u00d6"; // FIXME utf-8 literal
    tmp.setErrorMessage(utf8);

    serialize("VisitorInfoMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("VisitorInfoMessage", DocumentProtocol::MESSAGE_VISITORINFO, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<VisitorInfoMessage&>(*obj);
        ASSERT_EQ(ref.getFinishedBuckets().size(), 3u);
        EXPECT_EQ(ref.getFinishedBuckets()[0], document::BucketId(16, 1));
        EXPECT_EQ(ref.getFinishedBuckets()[1], document::BucketId(16, 2));
        EXPECT_EQ(ref.getFinishedBuckets()[2], document::BucketId(16, 4));
        EXPECT_EQ(ref.getErrorMessage(), utf8);
    }
}

TEST_F(Messages80Test, visitor_info_reply) {
    ASSERT_NO_FATAL_FAILURE(try_visitor_reply("VisitorInfoReply", DocumentProtocol::REPLY_VISITORINFO));
}

TEST_F(Messages80Test, document_list_message) {
    auto doc = createDoc(type_repo(), "testdoc", "id:scheme:testdoc:n=1234:1");
    DocumentListMessage::Entry entry(1234, std::move(doc), true);
    DocumentListMessage tmp(document::BucketId(17, 1234));
    tmp.getDocuments().push_back(std::move(entry));

    serialize("DocumentListMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("DocumentListMessage", DocumentProtocol::MESSAGE_DOCUMENTLIST, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<DocumentListMessage&>(*obj);
        ASSERT_EQ(ref.getDocuments().size(), 1u);
        EXPECT_EQ(ref.getDocuments()[0].getDocument()->getId().toString(), "id:scheme:testdoc:n=1234:1");
        EXPECT_EQ(ref.getDocuments()[0].getTimestamp(), 1234);
        EXPECT_TRUE(ref.getDocuments()[0].isRemoveEntry());
    }
}

TEST_F(Messages80Test, document_list_reply) {
    ASSERT_NO_FATAL_FAILURE(try_visitor_reply("DocumentListReply", DocumentProtocol::REPLY_DOCUMENTLIST));
}

TEST_F(Messages80Test, empty_buckets_message) {
    std::vector<document::BucketId> bids;
    for (size_t i=0; i < 13; ++i) {
        bids.emplace_back(16, i);
    }
    EmptyBucketsMessage msg(bids);

    serialize("EmptyBucketsMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("EmptyBucketsMessage", DocumentProtocol::MESSAGE_EMPTYBUCKETS, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<EmptyBucketsMessage&>(*obj);
        ASSERT_EQ(ref.getBucketIds().size(), 13u);
        for (size_t i = 0; i < 13; ++i) {
            EXPECT_EQ(ref.getBucketIds()[i], document::BucketId(16, i));
        }
    }
}

TEST_F(Messages80Test, empty_buckets_reply) {
    ASSERT_NO_FATAL_FAILURE(try_visitor_reply("EmptyBucketsReply", DocumentProtocol::REPLY_EMPTYBUCKETS));
}

TEST_F(Messages80Test, get_bucket_list_message) {
    GetBucketListMessage msg(document::BucketId(16, 123));
    msg.setBucketSpace("beartato");

    serialize("GetBucketListMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("GetBucketListMessage", DocumentProtocol::MESSAGE_GETBUCKETLIST, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetBucketListMessage&>(*obj);
        EXPECT_EQ(ref.getBucketId(), document::BucketId(16, 123));
        EXPECT_EQ(ref.getBucketSpace(), "beartato");
    }
}

TEST_F(Messages80Test, get_bucket_list_reply) {
    GetBucketListReply reply;
    reply.getBuckets().emplace_back(document::BucketId(16, 123), "foo");
    reply.getBuckets().emplace_back(document::BucketId(17, 1123), "bar");
    reply.getBuckets().emplace_back(document::BucketId(18, 11123), "zoink");

    serialize("GetBucketListReply", reply);

    for (auto lang : languages()) {
        auto obj = deserialize("GetBucketListReply", DocumentProtocol::REPLY_GETBUCKETLIST, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetBucketListReply&>(*obj);
        ASSERT_EQ(ref.getBuckets().size(), 3u);
        EXPECT_EQ(ref.getBuckets()[0], GetBucketListReply::BucketInfo(document::BucketId(16, 123), "foo"));
        EXPECT_EQ(ref.getBuckets()[1], GetBucketListReply::BucketInfo(document::BucketId(17, 1123), "bar"));
        EXPECT_EQ(ref.getBuckets()[2], GetBucketListReply::BucketInfo(document::BucketId(18, 11123), "zoink"));
    }
}

TEST_F(Messages80Test, get_bucket_state_message) {
    GetBucketStateMessage tmp;
    tmp.setBucketId(document::BucketId(16, 666));

    serialize("GetBucketStateMessage", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("GetBucketStateMessage", DocumentProtocol::MESSAGE_GETBUCKETSTATE, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetBucketStateMessage&>(*obj);
        EXPECT_EQ(ref.getBucketId().getUsedBits(), 16u);
        EXPECT_EQ(ref.getBucketId().getId(), 4611686018427388570ULL);
    }
}

TEST_F(Messages80Test, get_bucket_state_reply) {
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
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetBucketStateReply&>(*obj);
        ASSERT_EQ(ref.getBucketState().size(), 3u);
        EXPECT_EQ(ref.getBucketState()[0].getTimestamp(), 777u);
        EXPECT_FALSE(ref.getBucketState()[0].getDocumentId());
        EXPECT_EQ(ref.getBucketState()[0].getGlobalId(), foo);
        EXPECT_FALSE(ref.getBucketState()[0].isRemoveEntry());

        EXPECT_EQ(ref.getBucketState()[1].getTimestamp(), 888u);
        EXPECT_FALSE(ref.getBucketState()[1].getDocumentId());
        EXPECT_EQ(ref.getBucketState()[1].getGlobalId(), bar);
        EXPECT_TRUE(ref.getBucketState()[1].isRemoveEntry());

        EXPECT_EQ(ref.getBucketState()[2].getTimestamp(), 999u);
        EXPECT_EQ(ref.getBucketState()[2].getGlobalId(), baz.getGlobalId());
        EXPECT_FALSE(ref.getBucketState()[2].isRemoveEntry());
        ASSERT_TRUE(ref.getBucketState()[2].getDocumentId());
        EXPECT_EQ(*ref.getBucketState()[2].getDocumentId(), baz);
    }
}

TEST_F(Messages80Test, stat_bucket_message) {
    StatBucketMessage msg(document::BucketId(16, 123), "id.user=123");
    msg.setBucketSpace("andrei");

    serialize("StatBucketMessage", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("StatBucketMessage", DocumentProtocol::MESSAGE_STATBUCKET, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<StatBucketMessage&>(*obj);
        EXPECT_EQ(ref.getBucketId(), document::BucketId(16, 123));
        EXPECT_EQ(ref.getDocumentSelection(), "id.user=123");
        EXPECT_EQ(ref.getBucketSpace(), "andrei");
    }
}

TEST_F(Messages80Test, stat_bucket_reply) {
    StatBucketReply msg;
    msg.setResults("These are the votes of the Norwegian jury");

    serialize("StatBucketReply", msg);

    for (auto lang : languages()) {
        auto obj = deserialize("StatBucketReply", DocumentProtocol::REPLY_STATBUCKET, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<StatBucketReply&>(*obj);
        EXPECT_EQ(ref.getResults(), "These are the votes of the Norwegian jury");
    }
}

TEST_F(Messages80Test, wrong_distribution_reply) {
    WrongDistributionReply tmp("distributor:3 storage:2");

    serialize("WrongDistributionReply", tmp);

    for (auto lang : languages()) {
        auto obj = deserialize("WrongDistributionReply", DocumentProtocol::REPLY_WRONGDISTRIBUTION, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<WrongDistributionReply&>(*obj);
        EXPECT_EQ(ref.getSystemState(), "distributor:3 storage:2");
    }
}

TEST_F(Messages80Test, document_ignored_reply) {
    DocumentIgnoredReply tmp;
    serialize("DocumentIgnoredReply", tmp);
    for (auto lang : languages()) {
        auto obj = deserialize("DocumentIgnoredReply", DocumentProtocol::REPLY_DOCUMENTIGNORED, lang);
        EXPECT_TRUE(obj);
    }
}

void Messages80Test::try_visitor_reply(const std::string& filename, uint32_t type) {
    VisitorReply tmp(type);
    serialize(filename, tmp);

    for (auto lang : languages()) {
        auto obj = deserialize(filename, type, lang);
        ASSERT_TRUE(obj);
        auto* ptr = dynamic_cast<VisitorReply*>(obj.get());
        EXPECT_TRUE(ptr);
    }
}

} // documentapi
