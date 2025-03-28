// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for feedoperation.


#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/searchcore/proton/feedoperation/deletebucketoperation.h>
#include <vespa/searchcore/proton/feedoperation/joinbucketsoperation.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/newconfigoperation.h>
#include <vespa/searchcore/proton/feedoperation/noopoperation.h>
#include <vespa/searchcore/proton/feedoperation/pruneremoveddocumentsoperation.h>
#include <vespa/searchcore/proton/feedoperation/putoperation.h>
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchcore/proton/feedoperation/splitbucketoperation.h>
#include <vespa/searchcore/proton/feedoperation/updateoperation.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::BucketId;
using document::DataType;
using document::Document;
using document::DocumentType;
using document::DocumentId;
using document::DocumentUpdate;
using document::DocumentTypeRepo;
using document::GlobalId;
using document::StringFieldValue;
using document::AssignValueUpdate;
using document::FieldUpdate;
using document::config_builder::DocumenttypesConfigBuilderHelper;
using document::config_builder::Struct;
using document::config_builder::Map;
using search::DocumentIdT;
using namespace proton;

namespace {

struct MyStreamHandler : NewConfigOperation::IStreamHandler {
    using SerialNum = NewConfigOperation::SerialNum;
    void serializeConfig(SerialNum, vespalib::nbostream &) override {}
    void deserializeConfig(SerialNum, vespalib::nbostream &) override {}
};


const int32_t doc_type_id = 787121340;
const std::string type_name = "test";
const std::string header_name = type_name + ".header";
const std::string body_name = type_name + ".body";

const DocumentOperation::Timestamp TS_10(10);

const document::DocumentId docId("id::test::1");

BucketId toBucket(const GlobalId &gid)
{
    BucketId bucket(gid.convertToBucketId());
    bucket.setUsedBits(8);
    return bucket;
}

uint32_t getDocSize(const Document &doc)
{
    vespalib::nbostream tstream;
    doc.serialize(tstream);
    uint32_t docSize = tstream.size();
    assert(docSize != 0);
    return docSize;
}

uint32_t getDocIdSize(const DocumentId &doc_id)
{
    return doc_id.toString().size() + 1;
}

void assertDocumentOperation(DocumentOperation &op, BucketId expBucket, uint32_t expDocSize)
{
    EXPECT_EQ(expBucket, op.getBucketId());
    EXPECT_EQ(10u, op.getTimestamp());
    EXPECT_EQ(expDocSize, op.getSerializedDocSize());
    EXPECT_EQ(1u, op.getSubDbId());
    EXPECT_EQ(2u, op.getLid());
    EXPECT_EQ(3u, op.getPrevSubDbId());
    EXPECT_EQ(4u, op.getPrevLid());
}

std::unique_ptr<const DocumentTypeRepo>
makeDocTypeRepo()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name),
                     Struct(body_name)
                     .addField("string", DataType::T_STRING)
                     .addField("struct",
                               Struct("pair").addField("x", DataType::T_STRING).addField("y", DataType::T_STRING))
                     .addField("map", Map(DataType::T_STRING, DataType::T_STRING)));
    return std::make_unique<const DocumentTypeRepo>(builder.config());
}


struct Fixture
{
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType &_docType;

public:
    Fixture()
        : _repo(makeDocTypeRepo()),
          _docType(*_repo->getDocumentType(type_name))
    {
    }

    auto makeUpdate() {
        auto upd(std::make_shared<DocumentUpdate>(*_repo, _docType, docId));
        upd->addUpdate(FieldUpdate(upd->getType().getField("string")).
                       addUpdate(std::make_unique<AssignValueUpdate>(StringFieldValue::make("newval"))));
        return upd;
    }
    auto makeDoc() {
        auto doc(std::make_shared<Document>(*_repo, _docType, docId));
        doc->setValue("string", StringFieldValue("stringval"));
        return doc;
    }
};

TEST(FeedOperationTest, require_that_toString_on_derived_classes_are_meaningful)
{
    DocumentTypeRepo repo;
    BucketId bucket_id1(42);
    BucketId bucket_id2(43);
    BucketId bucket_id3(44);
    DocumentOperation::Timestamp timestamp(10);
    Document::SP doc(new Document);
    DbDocumentId db_doc_id;
    uint32_t sub_db_id = 1;
    MyStreamHandler stream_handler;
    DocumentIdT doc_id_limit = 15;
    DocumentId doc_id("id:ns:foo:::bar");
    auto update = std::make_shared<DocumentUpdate>(repo, *DataType::DOCUMENT, doc_id);

    EXPECT_EQ("DeleteBucket(BucketId(0x0000000000000000), serialNum=0)",
              DeleteBucketOperation().toString());
    EXPECT_EQ("DeleteBucket(BucketId(0x000000000000002a), serialNum=0)",
              DeleteBucketOperation(bucket_id1).toString());

    EXPECT_EQ("JoinBuckets("
              "source1=BucketId(0x0000000000000000), "
              "source2=BucketId(0x0000000000000000), "
              "target=BucketId(0x0000000000000000), serialNum=0)",
              JoinBucketsOperation().toString());
    EXPECT_EQ("JoinBuckets("
              "source1=BucketId(0x000000000000002a), "
              "source2=BucketId(0x000000000000002b), "
              "target=BucketId(0x000000000000002c), serialNum=0)",
              JoinBucketsOperation(bucket_id1, bucket_id2, bucket_id3)
              .toString());

    EXPECT_EQ("Move(NULL, BucketId(0x0000000000000000), timestamp=0, dbdId=(subDbId=0, lid=0), "
              "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
              MoveOperation().toString());
    EXPECT_EQ("Move(id::::, BucketId(0x000000000000002a), timestamp=10, dbdId=(subDbId=1, lid=0), "
              "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
              MoveOperation(bucket_id1, timestamp, doc,
                  db_doc_id, sub_db_id).toString());

    EXPECT_EQ("NewConfig(serialNum=64)",
              NewConfigOperation(64, stream_handler).toString());

    EXPECT_EQ("Noop(serialNum=32)", NoopOperation(32).toString());

    EXPECT_EQ("PruneRemovedDocuments(limitLid=0, subDbId=0, "
              "serialNum=0)",
              PruneRemovedDocumentsOperation().toString());
    EXPECT_EQ("PruneRemovedDocuments(limitLid=15, subDbId=1, "
              "serialNum=0)",
              PruneRemovedDocumentsOperation(
                  doc_id_limit, sub_db_id).toString());

    EXPECT_EQ("Put(NULL, BucketId(0x0000000000000000), timestamp=0, dbdId=(subDbId=0, lid=0), "
              "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
              PutOperation().toString());
    EXPECT_EQ("Put(id::::, BucketId(0x000000000000002a), timestamp=10, dbdId=(subDbId=0, lid=0), "
              "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
              PutOperation(bucket_id1, timestamp, std::move(doc)).toString());

    EXPECT_EQ("Remove(id::::, BucketId(0x0000000000000000), timestamp=0, dbdId=(subDbId=0, lid=0), "
              "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
              RemoveOperationWithDocId().toString());
    EXPECT_EQ("Remove(id:ns:foo:::bar, BucketId(0x000000000000002a), timestamp=10, dbdId=(subDbId=0, lid=0), "
              "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
              RemoveOperationWithDocId(bucket_id1, timestamp, doc_id).toString());

    EXPECT_EQ("SplitBucket("
              "source=BucketId(0x0000000000000000), "
              "target1=BucketId(0x0000000000000000), "
              "target2=BucketId(0x0000000000000000), serialNum=0)",
              SplitBucketOperation().toString());
    EXPECT_EQ("SplitBucket("
              "source=BucketId(0x000000000000002a), "
              "target1=BucketId(0x000000000000002b), "
              "target2=BucketId(0x000000000000002c), serialNum=0)",
              SplitBucketOperation(bucket_id1, bucket_id2, bucket_id3)
              .toString());
    EXPECT_EQ("Update(NULL, BucketId(0x0000000000000000), timestamp=0, dbdId=(subDbId=0, lid=0), "
              "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
              UpdateOperation().toString());
    EXPECT_EQ("Update(id:ns:foo:::bar, BucketId(0x000000000000002a), timestamp=10, dbdId=(subDbId=0, lid=0), "
              "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
              UpdateOperation(bucket_id1, timestamp, update).toString());

    EXPECT_EQ("CompactLidSpace(subDbId=2, lidLimit=99, serialNum=0)",
              CompactLidSpaceOperation(2, 99).toString());
}

TEST(FeedOperationTest, require_that_serialize_and_deserialize_works_for_CompactLidSpaceOperation)
{
    vespalib::nbostream stream;
    {
        CompactLidSpaceOperation op(2, 99);
        EXPECT_EQ(FeedOperation::COMPACT_LID_SPACE, op.getType());
        EXPECT_EQ(2u, op.getSubDbId());
        EXPECT_EQ(99u, op.getLidLimit());
        op.serialize(stream);
    }
    {
        const document::DocumentTypeRepo repo;
        CompactLidSpaceOperation op;
        op.deserialize(stream, repo);
        EXPECT_EQ(FeedOperation::COMPACT_LID_SPACE, op.getType());
        EXPECT_EQ(2u, op.getSubDbId());
        EXPECT_EQ(99u, op.getLidLimit());
    }
}

TEST(FeedOperationTest, require_that_we_can_serialize_and_deserialize_update_operations)
{
    Fixture f;
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    auto upd(f.makeUpdate());
    {
        UpdateOperation op(bucket, 10, upd);
        op.serialize(stream);
    }
    {
        UpdateOperation op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQ(*upd, *op.getUpdate());
        EXPECT_EQ(bucket, op.getBucketId());
        EXPECT_EQ(10u, op.getTimestamp());
    }
}

TEST(FeedOperationTest, require_that_we_can_serialize_and_deserialize_put_operations)
{
    Fixture f;
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    auto doc(f.makeDoc());
    uint32_t expSerializedDocSize = getDocSize(*doc);
    EXPECT_NE(0u, expSerializedDocSize);
    {
        PutOperation op(bucket, 10, doc);
        op.setDbDocumentId({1, 2});
        op.setPrevDbDocumentId({3, 4});
        EXPECT_EQ(0u, op.getSerializedDocSize());
        op.serialize(stream);
        EXPECT_EQ(expSerializedDocSize, op.getSerializedDocSize());
    }
    {
        PutOperation op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQ(*doc, *op.getDocument());
        assertDocumentOperation(op, bucket, expSerializedDocSize);
    }
}

TEST(FeedOperationTest, require_that_we_can_serialize_and_deserialize_move_operations)
{
    Fixture f;
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    auto doc(f.makeDoc());
    uint32_t expSerializedDocSize = getDocSize(*doc);
    EXPECT_NE(0u, expSerializedDocSize);
    {
        MoveOperation op(bucket, TS_10, doc, {3, 4}, 1);
        op.setTargetLid(2);
        EXPECT_EQ(0u, op.getSerializedDocSize());
        op.serialize(stream);
        EXPECT_EQ(expSerializedDocSize, op.getSerializedDocSize());
    }
    {
        MoveOperation op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQ(*doc, *op.getDocument());
        assertDocumentOperation(op, bucket, expSerializedDocSize);
    }
}

TEST(FeedOperationTest, require_that_we_can_serialize_and_deserialize_remove_operations)
{
    Fixture f;
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    uint32_t expSerializedDocSize = getDocIdSize(docId);
    EXPECT_NE(0u, expSerializedDocSize);
    {
        RemoveOperationWithDocId op(bucket, TS_10, docId);
        op.setDbDocumentId({1, 2});
        op.setPrevDbDocumentId({3, 4});
        EXPECT_EQ(0u, op.getSerializedDocSize());
        op.serialize(stream);
        EXPECT_EQ(expSerializedDocSize, op.getSerializedDocSize());
    }
    {
        RemoveOperationWithDocId op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQ(docId, op.getDocumentId());
        assertDocumentOperation(op, bucket, expSerializedDocSize);
    }
}

TEST(FeedOperationTest, require_that_we_can_serialize_and_deserialize_remove_by_gid_operations)
{
    Fixture f;
    vespalib::nbostream stream;
    GlobalId gid = docId.getGlobalId();
    BucketId bucket(toBucket(gid));
    uint32_t expSerializedDocSize = 25;
    std::string expDocType = "testdoc_type";
    EXPECT_NE(0u, expSerializedDocSize);
    {
        RemoveOperationWithGid op(bucket, TS_10, gid, expDocType);
        op.setPrevDbDocumentId({3, 4});
        EXPECT_EQ(0u, op.getSerializedDocSize());
        op.serialize(stream);
        EXPECT_EQ(expSerializedDocSize, op.getSerializedDocSize());
    }
    {
        RemoveOperationWithGid op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQ(gid, op.getGlobalId());
        EXPECT_EQ(expDocType, op.getDocType());
        EXPECT_EQ(bucket, op.getBucketId());
        EXPECT_EQ(10u, op.getTimestamp());
        EXPECT_EQ(expSerializedDocSize, op.getSerializedDocSize());
        EXPECT_FALSE(op.getValidDbdId());
        EXPECT_EQ(3u, op.getPrevSubDbId());
        EXPECT_EQ(4u, op.getPrevLid());
        EXPECT_TRUE(stream.empty());
    }
}

}
