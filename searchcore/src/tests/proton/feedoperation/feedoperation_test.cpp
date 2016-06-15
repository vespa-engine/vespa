// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for feedoperation.

#include <vespa/log/log.h>
LOG_SETUP("feedoperation_test");
#include <vespa/fastos/fastos.h>

#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <persistence/spi/types.h>
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
#include <vespa/vespalib/testkit/testapp.h>

using document::BucketId;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using search::DocumentIdT;
using storage::spi::Timestamp;
using namespace proton;

namespace {

struct MyStreamHandler : NewConfigOperation::IStreamHandler {
    typedef NewConfigOperation::SerialNum SerialNum;
    virtual void serializeConfig(SerialNum, vespalib::nbostream &) {}
    virtual void deserializeConfig(SerialNum, vespalib::nbostream &) {}
};

TEST("require that toString() on derived classes are meaningful")
{
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
    DocumentUpdate::SP update(new DocumentUpdate(*DataType::DOCUMENT, doc_id));

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

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
