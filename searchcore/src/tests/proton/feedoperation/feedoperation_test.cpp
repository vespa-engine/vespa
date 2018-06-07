// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/searchcore/proton/feedoperation/spoolerreplayoperation.h>
#include <vespa/searchcore/proton/feedoperation/updateoperation.h>
#include <vespa/searchcore/proton/feedoperation/wipehistoryoperation.h>
#include <vespa/searchlib/query/base.h>
#include <persistence/spi/types.h>
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
#include <vespa/vespalib/testkit/testapp.h>

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
using storage::spi::Timestamp;
using namespace proton;

namespace {

struct MyStreamHandler : NewConfigOperation::IStreamHandler {
    typedef NewConfigOperation::SerialNum SerialNum;
    virtual void serializeConfig(SerialNum, vespalib::nbostream &) override {}
    virtual void deserializeConfig(SerialNum, vespalib::nbostream &) override {}
};


const int32_t doc_type_id = 787121340;
const vespalib::string type_name = "test";
const vespalib::string header_name = type_name + ".header";
const vespalib::string body_name = type_name + ".body";

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
    EXPECT_EQUAL(expBucket, op.getBucketId());
    EXPECT_EQUAL(10u, op.getTimestamp().getValue());
    EXPECT_EQUAL(expDocSize, op.getSerializedDocSize());
    EXPECT_EQUAL(1u, op.getSubDbId());
    EXPECT_EQUAL(2u, op.getLid());
    EXPECT_EQUAL(3u, op.getPrevSubDbId());
    EXPECT_EQUAL(4u, op.getPrevLid());
}

std::unique_ptr<const DocumentTypeRepo>
makeDocTypeRepo()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name), Struct(body_name).
                     addField("string", DataType::T_STRING).
                     addField("struct", Struct("pair").
                              addField("x", DataType::T_STRING).
                              addField("y", DataType::T_STRING)).
                     addField("map", Map(DataType::T_STRING,
                                         DataType::T_STRING)));
    return std::unique_ptr<const DocumentTypeRepo>(new DocumentTypeRepo(builder.config()));
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
                       addUpdate(AssignValueUpdate(StringFieldValue("newval"))));
        return upd;
    }
    auto makeDoc() {
        auto doc(std::make_shared<Document>(_docType, docId));
        doc->setValue("string", StringFieldValue("stringval"));
        return doc;
    }
};

TEST("require that toString() on derived classes are meaningful")
{
    DocumentTypeRepo repo;
    BucketId bucket_id1(42);
    BucketId bucket_id2(43);
    BucketId bucket_id3(44);
    Timestamp timestamp(10);
    Document::SP doc(new Document);
    DbDocumentId db_doc_id;
    uint32_t sub_db_id = 1;
    MyStreamHandler stream_handler;
    DocumentIdT doc_id_limit = 15;
    DocumentId doc_id("doc:foo:bar");
    DocumentUpdate::SP update(new DocumentUpdate(repo, *DataType::DOCUMENT, doc_id));

    EXPECT_EQUAL("DeleteBucket(BucketId(0x0000000000000000), serialNum=0)",
                 DeleteBucketOperation().toString());
    EXPECT_EQUAL("DeleteBucket(BucketId(0x000000000000002a), serialNum=0)",
                 DeleteBucketOperation(bucket_id1).toString());

    EXPECT_EQUAL("JoinBuckets("
                 "source1=BucketId(0x0000000000000000), "
                 "source2=BucketId(0x0000000000000000), "
                 "target=BucketId(0x0000000000000000), serialNum=0)",
                 JoinBucketsOperation().toString());
    EXPECT_EQUAL("JoinBuckets("
                 "source1=BucketId(0x000000000000002a), "
                 "source2=BucketId(0x000000000000002b), "
                 "target=BucketId(0x000000000000002c), serialNum=0)",
                 JoinBucketsOperation(bucket_id1, bucket_id2, bucket_id3)
                 .toString());

    EXPECT_EQUAL("Move(NULL, BucketId(0x0000000000000000), timestamp=0, dbdId=(subDbId=0, lid=0), "
                 "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
                 MoveOperation().toString());
    EXPECT_EQUAL("Move(null::, BucketId(0x000000000000002a), timestamp=10, dbdId=(subDbId=1, lid=0), "
                 "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
                 MoveOperation(bucket_id1, timestamp, doc,
                               db_doc_id, sub_db_id).toString());

    EXPECT_EQUAL("NewConfig(serialNum=64)",
                 NewConfigOperation(64, stream_handler).toString());

    EXPECT_EQUAL("Noop(serialNum=32)", NoopOperation(32).toString());

    EXPECT_EQUAL("PruneRemovedDocuments(limitLid=0, subDbId=0, "
                 "serialNum=0)",
                 PruneRemovedDocumentsOperation().toString());
    EXPECT_EQUAL("PruneRemovedDocuments(limitLid=15, subDbId=1, "
                 "serialNum=0)",
                 PruneRemovedDocumentsOperation(
                         doc_id_limit, sub_db_id).toString());

    EXPECT_EQUAL("Put(NULL, BucketId(0x0000000000000000), timestamp=0, dbdId=(subDbId=0, lid=0), "
                 "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
                 PutOperation().toString());
    EXPECT_EQUAL("Put(null::, BucketId(0x000000000000002a), timestamp=10, dbdId=(subDbId=0, lid=0), "
                 "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
                 PutOperation(bucket_id1, timestamp, doc).toString());

    EXPECT_EQUAL("Remove(null::, BucketId(0x0000000000000000), timestamp=0, dbdId=(subDbId=0, lid=0), "
                 "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
                 RemoveOperation().toString());
    EXPECT_EQUAL("Remove(doc:foo:bar, BucketId(0x000000000000002a), timestamp=10, dbdId=(subDbId=0, lid=0), "
                 "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
                 RemoveOperation(bucket_id1, timestamp, doc_id).toString());

    EXPECT_EQUAL("SplitBucket("
                 "source=BucketId(0x0000000000000000), "
                 "target1=BucketId(0x0000000000000000), "
                 "target2=BucketId(0x0000000000000000), serialNum=0)",
                 SplitBucketOperation().toString());
    EXPECT_EQUAL("SplitBucket("
                 "source=BucketId(0x000000000000002a), "
                 "target1=BucketId(0x000000000000002b), "
                 "target2=BucketId(0x000000000000002c), serialNum=0)",
                 SplitBucketOperation(bucket_id1, bucket_id2, bucket_id3)
                 .toString());

    EXPECT_EQUAL("SpoolerReplayStart(spoolerSerialNum=0, serialNum=0)",
                 SpoolerReplayStartOperation().toString());
    EXPECT_EQUAL("SpoolerReplayStart(spoolerSerialNum=20, serialNum=10)",
                 SpoolerReplayStartOperation(10, 20).toString());

    EXPECT_EQUAL("SpoolerReplayComplete(spoolerSerialNum=0, serialNum=0)",
                 SpoolerReplayCompleteOperation().toString());
    EXPECT_EQUAL("SpoolerReplayComplete(spoolerSerialNum=2, serialNum=1)",
                 SpoolerReplayCompleteOperation(1, 2).toString());

    EXPECT_EQUAL("Update(NULL, BucketId(0x0000000000000000), timestamp=0, dbdId=(subDbId=0, lid=0), "
                 "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
                 UpdateOperation().toString());
    EXPECT_EQUAL("Update(doc:foo:bar, BucketId(0x000000000000002a), timestamp=10, dbdId=(subDbId=0, lid=0), "
                 "prevDbdId=(subDbId=0, lid=0), prevMarkedAsRemoved=false, prevTimestamp=0, serialNum=0)",
                 UpdateOperation(bucket_id1, timestamp, update).toString());

    EXPECT_EQUAL("WipeHistory(wipeTimeLimit=0, serialNum=0)",
                 WipeHistoryOperation().toString());
    EXPECT_EQUAL("WipeHistory(wipeTimeLimit=20, serialNum=10)",
                 WipeHistoryOperation(10, 20).toString());
    EXPECT_EQUAL("CompactLidSpace(subDbId=2, lidLimit=99, serialNum=0)",
                 CompactLidSpaceOperation(2, 99).toString());
}

TEST("require that serialize/deserialize works for CompactLidSpaceOperation")
{
    vespalib::nbostream stream;
    {
        CompactLidSpaceOperation op(2, 99);
        EXPECT_EQUAL(FeedOperation::COMPACT_LID_SPACE, op.getType());
        EXPECT_EQUAL(2u, op.getSubDbId());
        EXPECT_EQUAL(99u, op.getLidLimit());
        op.serialize(stream);
    }
    {
        const document::DocumentTypeRepo *repo = NULL;
        CompactLidSpaceOperation op;
        op.deserialize(stream, *repo);
        EXPECT_EQUAL(FeedOperation::COMPACT_LID_SPACE, op.getType());
        EXPECT_EQUAL(2u, op.getSubDbId());
        EXPECT_EQUAL(99u, op.getLidLimit());
    }
}

TEST_F("require that we can serialize and deserialize update operations", Fixture)
{
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    auto upd(f.makeUpdate());
    {
        UpdateOperation op(bucket, Timestamp(10), upd);
        op.serialize(stream);
    }
    {
        UpdateOperation op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQUAL(*upd, *op.getUpdate());
        EXPECT_EQUAL(bucket, op.getBucketId());
        EXPECT_EQUAL(10u, op.getTimestamp().getValue());
    }
}

TEST_F("require that we can deserialize old update operations", Fixture)
{
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    auto upd(f.makeUpdate());
    {
        UpdateOperation op(bucket, Timestamp(10), upd);
        op.serializeDocumentOperationOnly(stream);
        document::VespaDocumentSerializer serializer(stream);
        serializer.write42(*op.getUpdate());
    }
    {
        UpdateOperation op(FeedOperation::UPDATE_42);
        op.deserialize(stream, *f._repo);
        EXPECT_EQUAL(*upd, *op.getUpdate());
        EXPECT_EQUAL(bucket, op.getBucketId());
        EXPECT_EQUAL(10u, op.getTimestamp().getValue());
    }
}

TEST_F("require that we can serialize and deserialize put operations", Fixture)
{
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    auto doc(f.makeDoc());
    uint32_t expSerializedDocSize = getDocSize(*doc);
    EXPECT_NOT_EQUAL(0u, expSerializedDocSize);
    {
        PutOperation op(bucket, Timestamp(10), doc);
        op.setDbDocumentId({1, 2});
        op.setPrevDbDocumentId({3, 4});
        EXPECT_EQUAL(0u, op.getSerializedDocSize());
        op.serialize(stream);
        EXPECT_EQUAL(expSerializedDocSize, op.getSerializedDocSize());
    }
    {
        PutOperation op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQUAL(*doc, *op.getDocument());
        TEST_DO(assertDocumentOperation(op, bucket, expSerializedDocSize));
    }
}

TEST_F("require that we can serialize and deserialize move operations", Fixture)
{
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    auto doc(f.makeDoc());
    uint32_t expSerializedDocSize = getDocSize(*doc);
    EXPECT_NOT_EQUAL(0u, expSerializedDocSize);
    {
        MoveOperation op(bucket, Timestamp(10), doc, {3, 4}, 1);
        op.setTargetLid(2);
        EXPECT_EQUAL(0u, op.getSerializedDocSize());
        op.serialize(stream);
        EXPECT_EQUAL(expSerializedDocSize, op.getSerializedDocSize());
    }
    {
        MoveOperation op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQUAL(*doc, *op.getDocument());
        TEST_DO(assertDocumentOperation(op, bucket, expSerializedDocSize));
    }
}

TEST_F("require that we can serialize and deserialize remove operations", Fixture)
{
    vespalib::nbostream stream;
    BucketId bucket(toBucket(docId.getGlobalId()));
    uint32_t expSerializedDocSize = getDocIdSize(docId);
    EXPECT_NOT_EQUAL(0u, expSerializedDocSize);
    {
        RemoveOperation op(bucket, Timestamp(10), docId);
        op.setDbDocumentId({1, 2});
        op.setPrevDbDocumentId({3, 4});
        EXPECT_EQUAL(0u, op.getSerializedDocSize());
        op.serialize(stream);
        EXPECT_EQUAL(expSerializedDocSize, op.getSerializedDocSize());
    }
    {
        RemoveOperation op;
        op.deserialize(stream, *f._repo);
        EXPECT_EQUAL(docId, op.getDocumentId());
        TEST_DO(assertDocumentOperation(op, bucket, expSerializedDocSize));
    }
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
