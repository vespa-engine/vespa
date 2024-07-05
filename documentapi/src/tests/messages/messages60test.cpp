// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include "message_fixture.h"
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
        assert(ptr != nullptr);
    }
    const T *operator->() const noexcept { return ptr; }
};

namespace documentapi {

struct Messages60Test : public MessageFixture {
    vespalib::Version tested_protocol_version() const override { return {6, 221}; }

    static size_t serializedLength(const string& str) { return sizeof(int32_t) + str.size(); }

    void tryVisitorReply(const string& filename, uint32_t type);
};

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

TEST_F(Messages60Test, testGetBucketListMessage) {
    GetBucketListMessage msg(document::BucketId(16, 123));
    msg.setBucketSpace("beartato");
    EXPECT_EQ(MESSAGE_BASE_LENGTH + 12u + serializedLength("beartato"), serialize("GetBucketListMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketListMessage", DocumentProtocol::MESSAGE_GETBUCKETLIST, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetBucketListMessage&>(*obj);
        EXPECT_EQ(document::BucketId(16, 123), ref.getBucketId());
        EXPECT_EQ("beartato", ref.getBucketSpace());
    }
}

TEST_F(Messages60Test, testEmptyBucketsMessage) {
    std::vector<document::BucketId> bids;
    for (size_t i=0; i < 13; ++i) {
        bids.push_back(document::BucketId(16, i));
    }

    EmptyBucketsMessage msg(bids);

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 112u, serialize("EmptyBucketsMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("EmptyBucketsMessage", DocumentProtocol::MESSAGE_EMPTYBUCKETS, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<EmptyBucketsMessage&>(*obj);
        for (size_t i=0; i < 13; ++i) {
            EXPECT_EQ(document::BucketId(16, i), ref.getBucketIds()[i]);
        }
    }
}


TEST_F(Messages60Test, testStatBucketMessage) {
    StatBucketMessage msg(document::BucketId(16, 123), "id.user=123");
    msg.setBucketSpace("andrei");

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 27u + serializedLength("andrei"), serialize("StatBucketMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("StatBucketMessage", DocumentProtocol::MESSAGE_STATBUCKET, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<StatBucketMessage&>(*obj);
        EXPECT_EQ(document::BucketId(16, 123), ref.getBucketId());
        EXPECT_EQ("id.user=123", ref.getDocumentSelection());
        EXPECT_EQ("andrei", ref.getBucketSpace());
    }
}

TEST_F(Messages60Test, testCreateVisitorMessage) {
    CreateVisitorMessage tmp("SomeLibrary", "myvisitor", "newyork", "london");
    tmp.setDocumentSelection("true and false or true");
    tmp.getParameters().set("myvar", "somevalue");
    tmp.getParameters().set("anothervar", uint64_t(34));
    tmp.getBuckets().push_back(document::BucketId(16, 1234));
    tmp.setVisitRemoves(true);
    tmp.setFieldSet("foo bar");
    tmp.setMaxBucketsPerVisitor(2);
    tmp.setBucketSpace("bjarne");

    EXPECT_EQ(MESSAGE_BASE_LENGTH + serializedLength("bjarne") + 178, serialize("CreateVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("CreateVisitorMessage", DocumentProtocol::MESSAGE_CREATEVISITOR, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<CreateVisitorMessage&>(*obj);

        EXPECT_EQ(string("SomeLibrary"), ref.getLibraryName());
        EXPECT_EQ(string("myvisitor"), ref.getInstanceId());
        EXPECT_EQ(string("newyork"), ref.getControlDestination());
        EXPECT_EQ(string("london"), ref.getDataDestination());
        EXPECT_EQ(string("true and false or true"), ref.getDocumentSelection());
        EXPECT_EQ(string("foo bar"), ref.getFieldSet());
        EXPECT_EQ(uint32_t(8), ref.getMaximumPendingReplyCount());
        EXPECT_EQ(true, ref.visitRemoves());
        EXPECT_EQ(false, ref.visitInconsistentBuckets());
        EXPECT_EQ(size_t(1), ref.getBuckets().size());
        EXPECT_EQ(document::BucketId(16, 1234), ref.getBuckets()[0]);
        EXPECT_EQ(string("somevalue"), ref.getParameters().get("myvar"));
        EXPECT_EQ(uint64_t(34), ref.getParameters().get("anothervar", uint64_t(1)));
        EXPECT_EQ(uint32_t(2), ref.getMaxBucketsPerVisitor());
        EXPECT_EQ(string("bjarne"), ref.getBucketSpace());
    }
}

TEST_F(Messages60Test, testDestroyVisitorMessage) {
    DestroyVisitorMessage tmp("myvisitor");

    EXPECT_EQ(MESSAGE_BASE_LENGTH + (size_t)17, serialize("DestroyVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("DestroyVisitorMessage", DocumentProtocol::MESSAGE_DESTROYVISITOR, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<DestroyVisitorMessage&>(*obj);
        EXPECT_EQ(string("myvisitor"), ref.getInstanceId());
    }
}

TEST_F(Messages60Test, testDocumentListMessage) {
    document::Document::SP doc =
        createDoc(type_repo(), "testdoc", "id:scheme:testdoc:n=1234:1");
    DocumentListMessage::Entry entry(1234, doc, false);

    DocumentListMessage tmp(document::BucketId(16, 1234));
    tmp.getDocuments().push_back(entry);

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 69ul, serialize("DocumentListMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("DocumentListMessage", DocumentProtocol::MESSAGE_DOCUMENTLIST, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<DocumentListMessage&>(*obj);
        EXPECT_EQ("id:scheme:testdoc:n=1234:1", ref.getDocuments()[0].getDocument()->getId().toString());
        EXPECT_EQ(1234, ref.getDocuments()[0].getTimestamp());
        EXPECT_FALSE(ref.getDocuments()[0].isRemoveEntry());
    }
}

TEST_F(Messages60Test, testRemoveLocationMessage) {
    {
        document::BucketIdFactory factory;
        document::select::Parser parser(type_repo(), factory);
        RemoveLocationMessage msg(factory, parser, "id.group == \"mygroup\"");

        EXPECT_EQ(MESSAGE_BASE_LENGTH + 29u, serialize("RemoveLocationMessage", msg));
        for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
            mbus::Routable::UP obj = deserialize("RemoveLocationMessage", DocumentProtocol::MESSAGE_REMOVELOCATION, lang);
            ASSERT_TRUE(obj);
            auto& ref = dynamic_cast<RemoveLocationMessage&>(*obj);
            EXPECT_EQ(string("id.group == \"mygroup\""), ref.getDocumentSelection());
            // FIXME add to wire format, currently hardcoded.
            EXPECT_EQ(document::FixedBucketSpaces::default_space_name(), ref.getBucketSpace());
        }
    }
}

TEST_F(Messages60Test, testGetDocumentMessage) {
    GetDocumentMessage tmp(document::DocumentId("id:ns:testdoc::"), "foo bar");

    EXPECT_EQ(280u, sizeof(GetDocumentMessage));
    EXPECT_EQ(MESSAGE_BASE_LENGTH + (size_t)31, serialize("GetDocumentMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetDocumentMessage", DocumentProtocol::MESSAGE_GETDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetDocumentMessage&>(*obj);
        EXPECT_EQ(string("id:ns:testdoc::"), ref.getDocumentId().toString());
        EXPECT_EQ(string("foo bar"), ref.getFieldSet());
    }
}

TEST_F(Messages60Test, testMapVisitorMessage) {
    MapVisitorMessage tmp;
    tmp.getData().set("foo", 3);
    tmp.getData().set("bar", 5);

    EXPECT_EQ(MESSAGE_BASE_LENGTH + (size_t)32, serialize("MapVisitorMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("MapVisitorMessage", DocumentProtocol::MESSAGE_MAPVISITOR, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<MapVisitorMessage&>(*obj);
        EXPECT_EQ(3, ref.getData().get("foo", 0));
        EXPECT_EQ(5, ref.getData().get("bar", 0));
    }
}

TEST_F(Messages60Test, testCreateVisitorReply) {
    CreateVisitorReply reply(DocumentProtocol::REPLY_CREATEVISITOR);
    reply.setLastBucket(document::BucketId(16, 123));
    vdslib::VisitorStatistics vs;
    vs.setBucketsVisited(3);
    vs.setDocumentsVisited(1000);
    vs.setBytesVisited(1024000);
    vs.setDocumentsReturned(123);
    vs.setBytesReturned(512000);
    reply.setVisitorStatistics(vs);

    EXPECT_EQ(65u, serialize("CreateVisitorReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("CreateVisitorReply", DocumentProtocol::REPLY_CREATEVISITOR, lang);
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

TEST_F(Messages60Test, testPutDocumentMessage) {
    auto doc = createDoc(type_repo(), "testdoc", "id:ns:testdoc::");
    PutDocumentMessage msg(doc);

    msg.setTimestamp(666);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQ(64u, sizeof(vespalib::string));
    EXPECT_EQ(sizeof(vespalib::string), sizeof(TestAndSetCondition));
    EXPECT_EQ(112u, sizeof(DocumentMessage));
    EXPECT_EQ(sizeof(TestAndSetCondition) + sizeof(DocumentMessage), sizeof(TestAndSetMessage));
    EXPECT_EQ(sizeof(TestAndSetMessage) + 32, sizeof(PutDocumentMessage));
    int size_of_create_if_non_existent_flag = 1;
    EXPECT_EQ(MESSAGE_BASE_LENGTH +
                 45u +
                 serializedLength(msg.getCondition().getSelection()) +
                 size_of_create_if_non_existent_flag,
                 serialize("PutDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routableUp = deserialize("PutDocumentMessage", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang);
        ASSERT_TRUE(routableUp);
        auto& deserializedMsg = dynamic_cast<PutDocumentMessage &>(*routableUp);

        EXPECT_EQ(msg.getDocument().getType().getName(), deserializedMsg.getDocument().getType().getName());
        EXPECT_EQ(msg.getDocument().getId().toString(), deserializedMsg.getDocument().getId().toString());
        EXPECT_EQ(msg.getTimestamp(), deserializedMsg.getTimestamp());
        EXPECT_EQ(72u, deserializedMsg.getApproxSize());
        EXPECT_EQ(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
        EXPECT_EQ(false, deserializedMsg.get_create_if_non_existent());
    }

    //-------------------------------------------------------------------------

    PutDocumentMessage msg2(createDoc(type_repo(), "testdoc", "id:ns:testdoc::"));
    msg2.set_create_if_non_existent(true);
    uint32_t expected_message_size = MESSAGE_BASE_LENGTH + 45u +
        serializedLength(msg2.getCondition().getSelection()) +
        size_of_create_if_non_existent_flag;
    auto trunc1 = [](mbus::Blob x) noexcept { return truncate(std::move(x), 1); };
    auto pad1 = [](mbus::Blob x) noexcept { return pad(std::move(x), 1); };
    EXPECT_EQ(expected_message_size, serialize("PutDocumentMessage-create", msg2));
    EXPECT_EQ(expected_message_size - 1, serialize("PutDocumentMessage-create-truncate", msg2, trunc1));
    EXPECT_EQ(expected_message_size + 1, serialize("PutDocumentMessage-create-pad", msg2, pad1));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto decoded = Unwrap<PutDocumentMessage>(deserialize("PutDocumentMessage-create", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang));
        auto decoded_trunc = Unwrap<PutDocumentMessage>(deserialize("PutDocumentMessage-create-truncate", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang));
        auto decoded_pad = Unwrap<PutDocumentMessage>(deserialize("PutDocumentMessage-create-pad", DocumentProtocol::MESSAGE_PUTDOCUMENT, lang));
        EXPECT_EQ(true, decoded->get_create_if_non_existent());
        EXPECT_EQ(false, decoded_trunc->get_create_if_non_existent());
        EXPECT_EQ(true, decoded_pad->get_create_if_non_existent());
    }
}

TEST_F(Messages60Test, testGetBucketStateMessage) {
    GetBucketStateMessage tmp;
    tmp.setBucketId(document::BucketId(16, 666));
    EXPECT_EQ(MESSAGE_BASE_LENGTH + 12u, serialize("GetBucketStateMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketStateMessage", DocumentProtocol::MESSAGE_GETBUCKETSTATE, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetBucketStateMessage&>(*obj);

        EXPECT_EQ(16u, ref.getBucketId().getUsedBits());
        EXPECT_EQ(4611686018427388570ull, ref.getBucketId().getId());
    }
}

TEST_F(Messages60Test, testPutDocumentReply) {
    WriteDocumentReply reply(DocumentProtocol::REPLY_PUTDOCUMENT);
    reply.setHighestModificationTimestamp(30);

    EXPECT_EQ(13u, serialize("PutDocumentReply", reply));
    EXPECT_EQ(112u, sizeof(WriteDocumentReply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("PutDocumentReply", DocumentProtocol::REPLY_PUTDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<WriteDocumentReply&>(*obj);
        EXPECT_EQ(30u, ref.getHighestModificationTimestamp());
    }
}

TEST_F(Messages60Test, testUpdateDocumentReply) {
    UpdateDocumentReply reply;
    reply.setWasFound(false);
    reply.setHighestModificationTimestamp(30);

    EXPECT_EQ(14u, serialize("UpdateDocumentReply", reply));
    EXPECT_EQ(120u, sizeof(UpdateDocumentReply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("UpdateDocumentReply", DocumentProtocol::REPLY_UPDATEDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<UpdateDocumentReply&>(*obj);
        EXPECT_EQ(30u, ref.getHighestModificationTimestamp());
        EXPECT_EQ(false, ref.wasFound());
    }
}

TEST_F(Messages60Test, testRemoveDocumentMessage) {
    RemoveDocumentMessage msg(document::DocumentId("id:ns:testdoc::"));

    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQ(sizeof(TestAndSetMessage) + 104, sizeof(RemoveDocumentMessage));
    EXPECT_EQ(MESSAGE_BASE_LENGTH + size_t(20) + serializedLength(msg.getCondition().getSelection()), serialize("RemoveDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routablePtr = deserialize("RemoveDocumentMessage", DocumentProtocol::MESSAGE_REMOVEDOCUMENT, lang);
        ASSERT_TRUE(routablePtr);
        auto& ref = dynamic_cast<RemoveDocumentMessage&>(*routablePtr);
        EXPECT_EQ(string("id:ns:testdoc::"), ref.getDocumentId().toString());
        EXPECT_EQ(msg.getCondition().getSelection(), ref.getCondition().getSelection());
    }
}

TEST_F(Messages60Test, testRemoveDocumentReply) {
    RemoveDocumentReply reply;
    std::vector<uint64_t> ts;
    reply.setWasFound(false);
    reply.setHighestModificationTimestamp(30);
    EXPECT_EQ(120u, sizeof(RemoveDocumentReply));

    EXPECT_EQ(14u, serialize("RemoveDocumentReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("RemoveDocumentReply", DocumentProtocol::REPLY_REMOVEDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<RemoveDocumentReply&>(*obj);
        EXPECT_EQ(30u, ref.getHighestModificationTimestamp());
        EXPECT_EQ(false, ref.wasFound());
    }
}

TEST_F(Messages60Test, testUpdateDocumentMessage) {
    const DocumentTypeRepo & repo = type_repo();
    const document::DocumentType & docType = *repo.getDocumentType("testdoc");

    auto docUpdate = std::make_shared<document::DocumentUpdate>(repo, docType, document::DocumentId("id:ns:testdoc::"));

    docUpdate->addFieldPathUpdate(std::make_unique<document::RemoveFieldPathUpdate>("intfield", "testdoc.intfield > 0"));

    UpdateDocumentMessage msg(docUpdate);
    msg.setOldTimestamp(666u);
    msg.setNewTimestamp(777u);
    msg.setCondition(TestAndSetCondition("There's just one condition"));

    EXPECT_EQ(sizeof(TestAndSetMessage) + 40, sizeof(UpdateDocumentMessage));
    EXPECT_EQ(MESSAGE_BASE_LENGTH + 93u + serializedLength(msg.getCondition().getSelection()), serialize("UpdateDocumentMessage", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        auto routableUp = deserialize("UpdateDocumentMessage", DocumentProtocol::MESSAGE_UPDATEDOCUMENT, lang);
        ASSERT_TRUE(routableUp);
        auto& deserializedMsg = dynamic_cast<UpdateDocumentMessage&>(*routableUp);
        EXPECT_EQ(msg.getDocumentUpdate(), deserializedMsg.getDocumentUpdate());
        EXPECT_EQ(msg.getOldTimestamp(), deserializedMsg.getOldTimestamp());
        EXPECT_EQ(msg.getNewTimestamp(), deserializedMsg.getNewTimestamp());
        EXPECT_EQ(119u, deserializedMsg.getApproxSize());
        EXPECT_EQ(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
    }
}

TEST_F(Messages60Test, testQueryResultMessage) {
    QueryResultMessage srm;
    vdslib::SearchResult & sr(srm.getSearchResult());
    EXPECT_EQ(srm.getSequenceId(), 0u);
    EXPECT_EQ(sr.getHitCount(), 0u);
    EXPECT_EQ(sr.getAggregatorList().getSerializedSize(), 4u);
    EXPECT_EQ(sr.getSerializedSize(), 20u);
    EXPECT_EQ(srm.getApproxSize(), 28u);

    EXPECT_EQ(MESSAGE_BASE_LENGTH + size_t(32), serialize("QueryResultMessage-1", srm));

    mbus::Routable::UP routable = deserialize("QueryResultMessage-1", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    ASSERT_TRUE(routable);
    auto* dm = dynamic_cast<QueryResultMessage*>(routable.get());
    ASSERT_TRUE(dm);
    vdslib::SearchResult * dr(&dm->getSearchResult());
    EXPECT_EQ(dm->getSequenceId(), size_t(0));
    EXPECT_EQ(dr->getHitCount(), size_t(0));

    sr.addHit(0, "doc1", 89);
    sr.addHit(1, "doc17", 109);

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 63u, serialize("QueryResultMessage-2", srm));
    routable = deserialize("QueryResultMessage-2", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    ASSERT_TRUE(routable);
    dm = dynamic_cast<QueryResultMessage*>(routable.get());
    ASSERT_TRUE(dm);
    dr = &dm->getSearchResult();
    EXPECT_EQ(dr->getHitCount(), size_t(2));
    const char *docId;
    vdslib::SearchResult::RankType rank;
    dr->getHit(0, docId, rank);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQ(strcmp("doc1", docId), 0);
    dr->getHit(1, docId, rank);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQ(strcmp("doc17", docId), 0);

    sr.sort();

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 63u, serialize("QueryResultMessage-3", srm));
    routable = deserialize("QueryResultMessage-3", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    ASSERT_TRUE(routable);
    dm = dynamic_cast<QueryResultMessage*>(routable.get());
    ASSERT_TRUE(dm);
    dr = &dm->getSearchResult();
    EXPECT_EQ(dr->getHitCount(), size_t(2));
    dr->getHit(0, docId, rank);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQ(strcmp("doc17", docId), 0);
    dr->getHit(1, docId, rank);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQ(strcmp("doc1", docId), 0);

    QueryResultMessage srm2;
    vdslib::SearchResult & sr2(srm2.getSearchResult());
    sr2.addHit(0, "doc1", 89, "sortdata2", 9);
    sr2.addHit(1, "doc17", 109, "sortdata1", 9);
    sr2.addHit(2, "doc18", 90, "sortdata3", 9);

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 116u, serialize("QueryResultMessage-4", srm2));
    routable = deserialize("QueryResultMessage-4", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    ASSERT_TRUE(routable);
    dm = dynamic_cast<QueryResultMessage*>(routable.get());
    ASSERT_TRUE(dm);
    dr = &dm->getSearchResult();
    EXPECT_EQ(dr->getHitCount(), size_t(3));
    dr->getHit(0, docId, rank);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQ(strcmp("doc1", docId), 0);
    dr->getHit(1, docId, rank);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQ(strcmp("doc17", docId), 0);
    dr->getHit(2, docId, rank);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(90));
    EXPECT_EQ(strcmp("doc18", docId), 0);

    sr2.sort();
    const void *buf;
    size_t sz;
    sr2.getHit(0, docId, rank);
    sr2.getSortBlob(0, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata1", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQ(strcmp("doc17", docId), 0);
    sr2.getHit(1, docId, rank);
    sr2.getSortBlob(1, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata2", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQ(strcmp("doc1", docId), 0);
    sr2.getHit(2, docId, rank);
    sr2.getSortBlob(2, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata3", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(90));
    EXPECT_EQ(strcmp("doc18", docId), 0);

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 116u, serialize("QueryResultMessage-5", srm2));
    routable = deserialize("QueryResultMessage-5", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    ASSERT_TRUE(routable);
    dm = dynamic_cast<QueryResultMessage*>(routable.get());
    ASSERT_TRUE(dm);
    dr = &dm->getSearchResult();
    EXPECT_EQ(dr->getHitCount(), size_t(3));
    dr->getHit(0, docId, rank);
    dr->getSortBlob(0, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata1", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(109));
    EXPECT_EQ(strcmp("doc17", docId), 0);
    dr->getHit(1, docId, rank);
    dr->getSortBlob(1, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata2", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(89));
    EXPECT_EQ(strcmp("doc1", docId), 0);
    dr->getHit(2, docId, rank);
    dr->getSortBlob(2, buf, sz);
    EXPECT_EQ(sz, 9u);
    EXPECT_EQ(memcmp("sortdata3", buf, sz), 0);
    EXPECT_EQ(rank, vdslib::SearchResult::RankType(90));
    EXPECT_EQ(strcmp("doc18", docId), 0);

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

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 125u, serialize("QueryResultMessage-6", qrm3));
    routable = deserialize("QueryResultMessage-6", DocumentProtocol::MESSAGE_QUERYRESULT, LANG_CPP);
    ASSERT_TRUE(routable);
    dm = dynamic_cast<QueryResultMessage *>(routable.get());
    ASSERT_TRUE(dm);
    dr = &dm->getSearchResult();
    EXPECT_EQ(size_t(2), dr->getHitCount());
    dr->getHit(0, docId, rank);
    EXPECT_EQ(vdslib::SearchResult::RankType(7), rank);
    EXPECT_EQ(strcmp("doc2", docId), 0);
    dr->getHit(1, docId, rank);
    EXPECT_EQ(vdslib::SearchResult::RankType(5), rank);
    EXPECT_EQ(strcmp("doc1", docId), 0);
    auto mfv = dr->get_match_feature_values(0);
    EXPECT_EQ(2u, mfv.size());
    EXPECT_EQ(12.0, mfv[0].as_double());
    EXPECT_EQ("There", mfv[1].as_data().make_string());
    mfv = dr->get_match_feature_values(1);
    EXPECT_EQ(2u, mfv.size());
    EXPECT_EQ(1.0, mfv[0].as_double());
    EXPECT_EQ("Hi", mfv[1].as_data().make_string());
    const auto& mf_names = dr->get_match_features().names;
    EXPECT_EQ(2u, mf_names.size());
    EXPECT_EQ("foo", mf_names[0]);
    EXPECT_EQ("bar", mf_names[1]);
}

TEST_F(Messages60Test, testQueryResultReply) {
    ASSERT_NO_FATAL_FAILURE(tryVisitorReply("QueryResultReply", DocumentProtocol::REPLY_QUERYRESULT));
}

TEST_F(Messages60Test, testVisitorInfoMessage) {

    VisitorInfoMessage tmp;
    tmp.getFinishedBuckets().push_back(document::BucketId(16, 1));
    tmp.getFinishedBuckets().push_back(document::BucketId(16, 2));
    tmp.getFinishedBuckets().push_back(document::BucketId(16, 4));
    string utf8 = "error message: \u00e6\u00c6\u00f8\u00d8\u00e5\u00c5\u00f6\u00d6";
    tmp.setErrorMessage(utf8);

    EXPECT_EQ(MESSAGE_BASE_LENGTH + 67u, serialize("VisitorInfoMessage", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("VisitorInfoMessage", DocumentProtocol::MESSAGE_VISITORINFO, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<VisitorInfoMessage&>(*obj);
        EXPECT_EQ(document::BucketId(16, 1), ref.getFinishedBuckets()[0]);
        EXPECT_EQ(document::BucketId(16, 2), ref.getFinishedBuckets()[1]);
        EXPECT_EQ(document::BucketId(16, 4), ref.getFinishedBuckets()[2]);
        EXPECT_EQ(utf8, ref.getErrorMessage());
    }
}

TEST_F(Messages60Test, testDestroyVisitorReply) {
    ASSERT_NO_FATAL_FAILURE(tryVisitorReply("DestroyVisitorReply", DocumentProtocol::REPLY_DESTROYVISITOR));
}

TEST_F(Messages60Test, testDocumentIgnoredReply) {
    DocumentIgnoredReply tmp;
    serialize("DocumentIgnoredReply", tmp);
    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj(
                deserialize("DocumentIgnoredReply",
                            DocumentProtocol::REPLY_DOCUMENTIGNORED, lang));
        EXPECT_TRUE(obj);
    }
}

TEST_F(Messages60Test, testDocumentListReply) {
    ASSERT_NO_FATAL_FAILURE(tryVisitorReply("DocumentListReply", DocumentProtocol::REPLY_DOCUMENTLIST));
}

TEST_F(Messages60Test, testGetDocumentReply) {
    document::Document::SP doc =
        createDoc(type_repo(), "testdoc", "id:ns:testdoc::");
    GetDocumentReply tmp(doc);

    EXPECT_EQ(128u, sizeof(GetDocumentReply));
    EXPECT_EQ((size_t)47, serialize("GetDocumentReply", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetDocumentReply", DocumentProtocol::REPLY_GETDOCUMENT, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetDocumentReply&>(*obj);

        EXPECT_EQ(string("testdoc"), ref.getDocument().getType().getName());
        EXPECT_EQ(string("id:ns:testdoc::"), ref.getDocument().getId().toString());
    }
}

TEST_F(Messages60Test, testMapVisitorReply) {
    ASSERT_NO_FATAL_FAILURE(tryVisitorReply("MapVisitorReply", DocumentProtocol::REPLY_MAPVISITOR));
}

TEST_F(Messages60Test, testStatBucketReply) {
    StatBucketReply msg;
    msg.setResults("These are the votes of the Norwegian jury");

    EXPECT_EQ(50u, serialize("StatBucketReply", msg));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("StatBucketReply", DocumentProtocol::REPLY_STATBUCKET, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<StatBucketReply&>(*obj);
        EXPECT_EQ("These are the votes of the Norwegian jury", ref.getResults());
    }
}

TEST_F(Messages60Test, testVisitorInfoReply) {
    ASSERT_NO_FATAL_FAILURE(tryVisitorReply("VisitorInfoReply", DocumentProtocol::REPLY_VISITORINFO));
}

TEST_F(Messages60Test, testWrongDistributionReply) {
    WrongDistributionReply tmp("distributor:3 storage:2");

    serialize("WrongDistributionReply", tmp);

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("WrongDistributionReply", DocumentProtocol::REPLY_WRONGDISTRIBUTION, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<WrongDistributionReply&>(*obj);
        EXPECT_EQ(string("distributor:3 storage:2"), ref.getSystemState());
    }
}

TEST_F(Messages60Test, testGetBucketListReply) {
    GetBucketListReply reply;
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(16, 123), "foo"));
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(17, 1123), "bar"));
    reply.getBuckets().push_back(GetBucketListReply::BucketInfo(document::BucketId(18, 11123), "zoink"));

    EXPECT_EQ(56u, serialize("GetBucketListReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketListReply", DocumentProtocol::REPLY_GETBUCKETLIST, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetBucketListReply&>(*obj);

        EXPECT_EQ(ref.getBuckets()[0], GetBucketListReply::BucketInfo(document::BucketId(16, 123), "foo"));
        EXPECT_EQ(ref.getBuckets()[1], GetBucketListReply::BucketInfo(document::BucketId(17, 1123), "bar"));
        EXPECT_EQ(ref.getBuckets()[2], GetBucketListReply::BucketInfo(document::BucketId(18, 11123), "zoink"));
    }
}

TEST_F(Messages60Test, testGetBucketStateReply) {
    document::GlobalId foo = document::DocumentId("id:ns:testdoc::foo").getGlobalId();
    document::GlobalId bar = document::DocumentId("id:ns:testdoc::bar").getGlobalId();

    GetBucketStateReply reply;
    reply.getBucketState().push_back(DocumentState(foo, 777, false));
    reply.getBucketState().push_back(DocumentState(bar, 888, true));
    EXPECT_EQ(53u, serialize("GetBucketStateReply", reply));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("GetBucketStateReply", DocumentProtocol::REPLY_GETBUCKETSTATE, lang);
        ASSERT_TRUE(obj);
        auto& ref = dynamic_cast<GetBucketStateReply&>(*obj);

        EXPECT_EQ(777u, ref.getBucketState()[0].getTimestamp());
        EXPECT_EQ(foo, ref.getBucketState()[0].getGlobalId());
        EXPECT_EQ(false, ref.getBucketState()[0].isRemoveEntry());
        EXPECT_EQ(888u, ref.getBucketState()[1].getTimestamp());
        EXPECT_EQ(bar, ref.getBucketState()[1].getGlobalId());
        EXPECT_EQ(true, ref.getBucketState()[1].isRemoveEntry());
    }
}

TEST_F(Messages60Test, testEmptyBucketsReply) {
    ASSERT_NO_FATAL_FAILURE(tryVisitorReply("EmptyBucketsReply", DocumentProtocol::REPLY_EMPTYBUCKETS));
}

TEST_F(Messages60Test, testRemoveLocationReply) {
    DocumentReply tmp(DocumentProtocol::REPLY_REMOVELOCATION);

    EXPECT_EQ((uint32_t)5, serialize("RemoveLocationReply", tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize("RemoveLocationReply", DocumentProtocol::REPLY_REMOVELOCATION, lang);
        EXPECT_TRUE(obj);
    }
}



////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

void
Messages60Test::tryVisitorReply(const string& filename, uint32_t type)
{
    VisitorReply tmp(type);
    EXPECT_EQ((uint32_t)5, serialize(filename, tmp));

    for (uint32_t lang = 0; lang < NUM_LANGUAGES; ++lang) {
        mbus::Routable::UP obj = deserialize(filename, type, lang);
        ASSERT_TRUE(obj);
        auto* ref = dynamic_cast<VisitorReply*>(obj.get());
        EXPECT_TRUE(ref);
    }
}

} // documentapi
