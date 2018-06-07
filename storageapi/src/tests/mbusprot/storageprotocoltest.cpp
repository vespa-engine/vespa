// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/internal.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/storageapi/mbusprot/storageprotocol.h>
#include <vespa/storageapi/mbusprot/storagecommand.h>
#include <vespa/storageapi/mbusprot/storagereply.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/document.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <iomanip>
#include <sstream>

using std::shared_ptr;
using document::BucketSpace;
using document::ByteBuffer;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using storage::lib::ClusterState;
using vespalib::string;

namespace storage {
namespace api {

struct StorageProtocolTest : public CppUnit::TestFixture {
    document::TestDocMan _docMan;
    document::Document::SP _testDoc;
    document::DocumentId _testDocId;
    document::Bucket  _bucket;
    vespalib::Version _version5_0{5, 0, 12};
    vespalib::Version _version5_1{5, 1, 0};
    vespalib::Version _version5_2{5, 93, 30};
    vespalib::Version _version6_0{6, 240, 0};
    documentapi::LoadTypeSet _loadTypes;
    mbusprot::StorageProtocol _protocol;
    static std::vector<std::string> _nonVerboseMessageStrings;
    static std::vector<std::string> _verboseMessageStrings;
    static std::vector<char> _serialization50;
    static auto constexpr CONDITION_STRING = "There's just one condition";

    StorageProtocolTest()
        : _docMan(),
          _testDoc(_docMan.createDocument()),
          _testDocId(_testDoc->getId()),
          _bucket(makeDocumentBucket(document::BucketId(16, 0x51))),
          _protocol(_docMan.getTypeRepoSP(), _loadTypes, true)
    {
        _loadTypes.addLoadType(34, "foo", documentapi::Priority::PRI_NORMAL_2);
    }

    template<typename Command>
    std::shared_ptr<Command> copyCommand(const std::shared_ptr<Command>&, vespalib::Version);
    template<typename Reply>
    std::shared_ptr<Reply> copyReply(const std::shared_ptr<Reply>&);
    void recordOutput(const api::StorageMessage& msg);

    void recordSerialization50();

    void testWriteSerialization50();
    void testAddress50();
    void testStringOutputs();

    void testPut51();
    void testUpdate51();
    void testGet51();
    void testRemove51();
    void testRevert51();
    void testRequestBucketInfo51();
    void testNotifyBucketChange51();
    void testCreateBucket51();
    void testDeleteBucket51();
    void testMergeBucket51();
    void testGetBucketDiff51();
    void testApplyBucketDiff51();
    void testSplitBucket51();
    void testSplitBucketChain51();
    void testJoinBuckets51();
    void testBatchPutRemove51();
    void testCreateVisitor51();
    void testDestroyVisitor51();
    void testRemoveLocation51();
    void testInternalMessage();
    void testSetBucketState51();

    void testPutCommand52();
    void testUpdateCommand52();
    void testRemoveCommand52();

    void testPutCommandWithBucketSpace6_0();
    void testCreateVisitorWithBucketSpace6_0();
    void testRequestBucketInfoWithBucketSpace6_0();

    void serialized_size_is_used_to_set_approx_size_of_storage_message();

    CPPUNIT_TEST_SUITE(StorageProtocolTest);

    // Enable to see string outputs of messages
    // CPPUNIT_TEST_DISABLED(testStringOutputs);

    // Enable this to write 5.0 serialization to disk
    // CPPUNIT_TEST_DISABLED(testWriteSerialization50);
    // CPPUNIT_TEST_DISABLED(testAddress50);

    // 5.1 tests
    CPPUNIT_TEST(testPut51);
    CPPUNIT_TEST(testUpdate51);
    CPPUNIT_TEST(testGet51);
    CPPUNIT_TEST(testRemove51);
    CPPUNIT_TEST(testRevert51);
    CPPUNIT_TEST(testRequestBucketInfo51);
    CPPUNIT_TEST(testNotifyBucketChange51);
    CPPUNIT_TEST(testCreateBucket51);
    CPPUNIT_TEST(testDeleteBucket51);
    CPPUNIT_TEST(testMergeBucket51);
    CPPUNIT_TEST(testGetBucketDiff51);
    CPPUNIT_TEST(testApplyBucketDiff51);
    CPPUNIT_TEST(testSplitBucket51);
    CPPUNIT_TEST(testJoinBuckets51);
    CPPUNIT_TEST(testCreateVisitor51);
    CPPUNIT_TEST(testDestroyVisitor51);
    CPPUNIT_TEST(testRemoveLocation51);
    CPPUNIT_TEST(testBatchPutRemove51);
    CPPUNIT_TEST(testInternalMessage);
    CPPUNIT_TEST(testSetBucketState51);

    // 5.2 tests
    CPPUNIT_TEST(testPutCommand52);
    CPPUNIT_TEST(testUpdateCommand52);
    CPPUNIT_TEST(testRemoveCommand52);

    // 6.0 tests
    CPPUNIT_TEST(testPutCommandWithBucketSpace6_0);
    CPPUNIT_TEST(testCreateVisitorWithBucketSpace6_0);
    CPPUNIT_TEST(testRequestBucketInfoWithBucketSpace6_0);

    CPPUNIT_TEST(serialized_size_is_used_to_set_approx_size_of_storage_message);

    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(StorageProtocolTest);

std::vector<std::string> StorageProtocolTest::_nonVerboseMessageStrings;
std::vector<std::string> StorageProtocolTest::_verboseMessageStrings;
std::vector<char> StorageProtocolTest::_serialization50;

void
StorageProtocolTest::recordOutput(const api::StorageMessage& msg)
{
    std::ostringstream ost;
    ost << "  ";
    msg.print(ost, false, "  ");
    _nonVerboseMessageStrings.push_back(ost.str());
    ost.str("");
    ost << "  ";
    msg.print(ost, true, "  ");
    _verboseMessageStrings.push_back(ost.str());
}

namespace {

    bool debug = false;

    struct ScopedName {
        std::string _name;

        ScopedName(const std::string& s) : _name(s) {
            if (debug) std::cerr << "Starting test " << _name << "\n";
        }
        ~ScopedName() {
            if (debug) std::cerr << "Finished test " << _name << "\n";
        }
    };

} // Anonymous namespace

namespace {
    mbus::Message::UP lastCommand;
    mbus::Reply::UP lastReply;
}

void
StorageProtocolTest::testAddress50()
{
    StorageMessageAddress address("foo", lib::NodeType::STORAGE, 3);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("storage/cluster.foo/storage/3/default"),
                         address.getRoute().toString());
}

template<typename Command> std::shared_ptr<Command>
StorageProtocolTest::copyCommand(const std::shared_ptr<Command>& m, vespalib::Version version)
{
    mbus::Message::UP mbusMessage(new mbusprot::StorageCommand(m));
    mbus::Blob blob = _protocol.encode(version, *mbusMessage);
    mbus::Routable::UP copy(_protocol.decode(version, blob));

    CPPUNIT_ASSERT(copy.get());

    mbusprot::StorageCommand* copy2(dynamic_cast<mbusprot::StorageCommand*>(copy.get()));
    CPPUNIT_ASSERT(copy2 != 0);

    StorageCommand::SP internalMessage(copy2->getCommand());
    lastCommand = std::move(mbusMessage);

    return std::dynamic_pointer_cast<Command>(internalMessage);
}

template<typename Reply> std::shared_ptr<Reply>
StorageProtocolTest::copyReply(const std::shared_ptr<Reply>& m)
{
    mbus::Reply::UP mbusMessage(new mbusprot::StorageReply(m));
    mbus::Blob blob = _protocol.encode(_version5_1, *mbusMessage);
    mbus::Routable::UP copy(_protocol.decode(_version5_1, blob));
    CPPUNIT_ASSERT(copy.get());
    mbusprot::StorageReply* copy2(
            dynamic_cast<mbusprot::StorageReply*>(copy.get()));
    CPPUNIT_ASSERT(copy2 != 0);
    copy2->setMessage(std::move(lastCommand));
    StorageReply::SP internalMessage(copy2->getReply());
    lastReply = std::move(mbusMessage);
    lastCommand = copy2->getMessage();
    return std::dynamic_pointer_cast<Reply>(internalMessage);
}

void
StorageProtocolTest::recordSerialization50()
{
    assert(lastCommand.get());
    assert(lastReply.get());
    for (uint32_t j=0; j<2; ++j) {
        mbusprot::StorageMessage& msg(j == 0
                ? dynamic_cast<mbusprot::StorageMessage&>(*lastCommand)
                : dynamic_cast<mbusprot::StorageMessage&>(*lastReply));
        msg.getInternalMessage()->forceMsgId(0);
        mbus::Routable& routable(j == 0
                ? dynamic_cast<mbus::Routable&>(*lastCommand)
                : dynamic_cast<mbus::Routable&>(*lastReply));
        mbus::Blob blob = _protocol.encode(_version5_0, routable);
        _serialization50.push_back('\n');
        std::string type(msg.getInternalMessage()->getType().toString());
        for (uint32_t i=0, n=type.size(); i<n; ++i) {
            _serialization50.push_back(type[i]);
        }
        _serialization50.push_back('\n');

        for (uint32_t i=0, n=blob.size(); i<n; ++i) {
            _serialization50.push_back(blob.data()[i]);
        }
    }
}

void
StorageProtocolTest::testPut51()
{
    ScopedName test("testPut51");
    PutCommand::SP cmd(new PutCommand(_bucket, _testDoc, 14));
    cmd->setUpdateTimestamp(Timestamp(13));
    cmd->setLoadType(_loadTypes["foo"]);
    PutCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(*_testDoc, *cmd2->getDocument());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo"), cmd2->getLoadType().getName());
    CPPUNIT_ASSERT_EQUAL(Timestamp(14), cmd2->getTimestamp());
    CPPUNIT_ASSERT_EQUAL(Timestamp(13), cmd2->getUpdateTimestamp());

    PutReply::SP reply(new PutReply(*cmd2));
    CPPUNIT_ASSERT(reply->hasDocument());
    CPPUNIT_ASSERT_EQUAL(*_testDoc, *reply->getDocument());
    PutReply::SP reply2(copyReply(reply));
    CPPUNIT_ASSERT(reply2->hasDocument());
    CPPUNIT_ASSERT_EQUAL(*_testDoc, *reply->getDocument());
    CPPUNIT_ASSERT_EQUAL(_testDoc->getId(), reply2->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(Timestamp(14), reply2->getTimestamp());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testUpdate51()
{
    ScopedName test("testUpdate51");
    document::DocumentUpdate::SP update(new document::DocumentUpdate(_docMan.getTypeRepo(), *_testDoc->getDataType(), _testDoc->getId()));
    std::shared_ptr<document::AssignValueUpdate> assignUpdate(new document::AssignValueUpdate(document::IntFieldValue(17)));
    document::FieldUpdate fieldUpdate(_testDoc->getField("headerval"));
    fieldUpdate.addUpdate(*assignUpdate);
    update->addUpdate(fieldUpdate);

    update->addFieldPathUpdate(document::FieldPathUpdate::CP(
                    new document::RemoveFieldPathUpdate("headerval", "testdoctype1.headerval > 0")));

    UpdateCommand::SP cmd(new UpdateCommand(_bucket, update, 14));
    CPPUNIT_ASSERT_EQUAL(Timestamp(0), cmd->getOldTimestamp());
    cmd->setOldTimestamp(10);
    UpdateCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(_testDocId, cmd2->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(Timestamp(14), cmd2->getTimestamp());
    CPPUNIT_ASSERT_EQUAL(Timestamp(10), cmd2->getOldTimestamp());
    CPPUNIT_ASSERT_EQUAL(*update, *cmd2->getUpdate());

    UpdateReply::SP reply(new UpdateReply(*cmd2, 8));
    UpdateReply::SP reply2(copyReply(reply));
    CPPUNIT_ASSERT_EQUAL(_testDocId, reply2->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(Timestamp(14), reply2->getTimestamp());
    CPPUNIT_ASSERT_EQUAL(Timestamp(8), reply->getOldTimestamp());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testGet51()
{
    ScopedName test("testGet51");
    GetCommand::SP cmd(new GetCommand(_bucket, _testDocId, "foo,bar,vekterli", 123));
    GetCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(_testDocId, cmd2->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(Timestamp(123), cmd2->getBeforeTimestamp());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo,bar,vekterli"), cmd2->getFieldSet());

    GetReply::SP reply(new GetReply(*cmd2, _testDoc, 100));
    GetReply::SP reply2(copyReply(reply));
    CPPUNIT_ASSERT(reply2.get());
    CPPUNIT_ASSERT(reply2->getDocument().get());
    CPPUNIT_ASSERT_EQUAL(*_testDoc, *reply2->getDocument());
    CPPUNIT_ASSERT_EQUAL(_testDoc->getId(), reply2->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(Timestamp(123), reply2->getBeforeTimestamp());
    CPPUNIT_ASSERT_EQUAL(Timestamp(100), reply2->getLastModifiedTimestamp());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testRemove51()
{
    ScopedName test("testRemove51");
    RemoveCommand::SP cmd(new RemoveCommand(_bucket, _testDocId, 159));
    RemoveCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(_testDocId, cmd2->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(Timestamp(159), cmd2->getTimestamp());

    RemoveReply::SP reply(new RemoveReply(*cmd2, 48));
    reply->setBucketInfo(BucketInfo(1,2,3,4,5, true, false, 48));

    RemoveReply::SP reply2(copyReply(reply));
    CPPUNIT_ASSERT_EQUAL(_testDocId, reply2->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(Timestamp(159), reply2->getTimestamp());
    CPPUNIT_ASSERT_EQUAL(Timestamp(48), reply2->getOldTimestamp());
    CPPUNIT_ASSERT_EQUAL(BucketInfo(1,2,3,4,5, true, false, 48),
                         reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testRevert51()
{
    ScopedName test("testRevertCommand51");
    std::vector<Timestamp> tokens;
    tokens.push_back(59);
    RevertCommand::SP cmd(new RevertCommand(_bucket, tokens));
    RevertCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(tokens, cmd2->getRevertTokens());

    RevertReply::SP reply(new RevertReply(*cmd2));
    BucketInfo info(0x12345432, 101, 520);
    reply->setBucketInfo(info);
    RevertReply::SP reply2(copyReply(reply));

    CPPUNIT_ASSERT_EQUAL(info, reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testRequestBucketInfo51()
{
    ScopedName test("testRequestBucketInfo51");
    {
        std::vector<document::BucketId> ids;
        ids.push_back(document::BucketId(3));
        ids.push_back(document::BucketId(7));
        RequestBucketInfoCommand::SP cmd(new RequestBucketInfoCommand(makeBucketSpace(), ids));
        RequestBucketInfoCommand::SP cmd2(copyCommand(cmd, _version5_1));
        CPPUNIT_ASSERT_EQUAL(ids, cmd2->getBuckets());
        CPPUNIT_ASSERT(!cmd2->hasSystemState());

        recordOutput(*cmd2);
    }
    {
        ClusterState state("distributor:3 .1.s:d");
        RequestBucketInfoCommand::SP cmd(new RequestBucketInfoCommand(
                                                 makeBucketSpace(),
                                                 3, state, "14"));
        RequestBucketInfoCommand::SP cmd2(copyCommand(cmd, _version5_1));
        CPPUNIT_ASSERT(cmd2->hasSystemState());
        CPPUNIT_ASSERT_EQUAL(uint16_t(3), cmd2->getDistributor());
        CPPUNIT_ASSERT_EQUAL(state, cmd2->getSystemState());
        CPPUNIT_ASSERT_EQUAL(size_t(0), cmd2->getBuckets().size());

        RequestBucketInfoReply::SP reply(new RequestBucketInfoReply(*cmd));
        RequestBucketInfoReply::Entry e;
        e._bucketId = document::BucketId(4);
        const uint64_t lastMod = 0x1337cafe98765432ULL;
        e._info = BucketInfo(43, 24, 123, 44, 124, false, true, lastMod);
        reply->getBucketInfo().push_back(e);
        RequestBucketInfoReply::SP reply2(copyReply(reply));
        CPPUNIT_ASSERT_EQUAL(size_t(1), reply2->getBucketInfo().size());
        auto& entries(reply2->getBucketInfo());
        CPPUNIT_ASSERT_EQUAL(e, entries[0]);
        // "Last modified" not counted by operator== for some reason. Testing
        // separately until we can figure out if this is by design or not.
        CPPUNIT_ASSERT_EQUAL(lastMod, entries[0]._info.getLastModified());

        recordOutput(*cmd2);
        recordOutput(*reply2);
        recordSerialization50();
    }
}

void
StorageProtocolTest::testNotifyBucketChange51()
{
    ScopedName test("testNotifyBucketChange51");
    BucketInfo info(2, 3, 4);
    document::BucketId modifiedBucketId(20, 1000);
    document::Bucket modifiedBucket(makeDocumentBucket(modifiedBucketId));
    NotifyBucketChangeCommand::SP cmd(new NotifyBucketChangeCommand(
                                          modifiedBucket, info));
    NotifyBucketChangeCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(document::BucketId(20, 1000),
                         cmd2->getBucketId());
    CPPUNIT_ASSERT_EQUAL(info, cmd2->getBucketInfo());

    NotifyBucketChangeReply::SP reply(new NotifyBucketChangeReply(*cmd));
    NotifyBucketChangeReply::SP reply2(copyReply(reply));

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testCreateBucket51()
{
    ScopedName test("testCreateBucket51");
    document::BucketId bucketId(623);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    CreateBucketCommand::SP cmd(new CreateBucketCommand(bucket));
    CreateBucketCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(bucketId, cmd2->getBucketId());

    CreateBucketReply::SP reply(new CreateBucketReply(*cmd));
    CreateBucketReply::SP reply2(copyReply(reply));
    CPPUNIT_ASSERT_EQUAL(bucketId, reply2->getBucketId());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testDeleteBucket51()
{
    ScopedName test("testDeleteBucket51");
    document::BucketId bucketId(623);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    DeleteBucketCommand::SP cmd(new DeleteBucketCommand(bucket));
    BucketInfo info(0x100, 200, 300);
    cmd->setBucketInfo(info);
    DeleteBucketCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(bucketId, cmd2->getBucketId());
    CPPUNIT_ASSERT_EQUAL(info, cmd2->getBucketInfo());

    DeleteBucketReply::SP reply(new DeleteBucketReply(*cmd));
    // Not set automatically by constructor
    reply->setBucketInfo(cmd2->getBucketInfo());
    DeleteBucketReply::SP reply2(copyReply(reply));
    CPPUNIT_ASSERT_EQUAL(bucketId, reply2->getBucketId());
    CPPUNIT_ASSERT_EQUAL(info, reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testMergeBucket51()
{
    ScopedName test("testMergeBucket51");
    document::BucketId bucketId(623);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    typedef api::MergeBucketCommand::Node Node;
    std::vector<Node> nodes;
    nodes.push_back(Node(4, false));
    nodes.push_back(Node(13, true));
    nodes.push_back(Node(26, true));

    std::vector<uint16_t> chain;
    // Not a valid chain wrt. the nodes, but just want to have unique values
    chain.push_back(7);
    chain.push_back(14);

    MergeBucketCommand::SP cmd(
            new MergeBucketCommand(bucket, nodes, Timestamp(1234), 567, chain));
    MergeBucketCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(bucketId, cmd2->getBucketId());
    CPPUNIT_ASSERT_EQUAL(nodes, cmd2->getNodes());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), cmd2->getMaxTimestamp());
    CPPUNIT_ASSERT_EQUAL(uint32_t(567), cmd2->getClusterStateVersion());
    CPPUNIT_ASSERT_EQUAL(chain, cmd2->getChain());

    MergeBucketReply::SP reply(new MergeBucketReply(*cmd));
    MergeBucketReply::SP reply2(copyReply(reply));
    CPPUNIT_ASSERT_EQUAL(bucketId, reply2->getBucketId());
    CPPUNIT_ASSERT_EQUAL(nodes, reply2->getNodes());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1234), reply2->getMaxTimestamp());
    CPPUNIT_ASSERT_EQUAL(uint32_t(567), reply2->getClusterStateVersion());
    CPPUNIT_ASSERT_EQUAL(chain, reply2->getChain());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testSplitBucket51()
{
    ScopedName test("testSplitBucket51");

    document::BucketId bucketId(16, 0);
    document::Bucket bucket(makeDocumentBucket(bucketId));
    SplitBucketCommand::SP cmd(new SplitBucketCommand(bucket));
    CPPUNIT_ASSERT_EQUAL(0u, (uint32_t) cmd->getMinSplitBits());
    CPPUNIT_ASSERT_EQUAL(58u, (uint32_t) cmd->getMaxSplitBits());
    CPPUNIT_ASSERT_EQUAL(std::numeric_limits<uint32_t>().max(),
                         cmd->getMinByteSize());
    CPPUNIT_ASSERT_EQUAL(std::numeric_limits<uint32_t>().max(),
                         cmd->getMinDocCount());
    cmd->setMinByteSize(1000);
    cmd->setMinDocCount(5);
    cmd->setMaxSplitBits(40);
    cmd->setMinSplitBits(20);
    SplitBucketCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(20u, (uint32_t) cmd2->getMinSplitBits());
    CPPUNIT_ASSERT_EQUAL(40u, (uint32_t) cmd2->getMaxSplitBits());
    CPPUNIT_ASSERT_EQUAL(1000u, cmd2->getMinByteSize());
    CPPUNIT_ASSERT_EQUAL(5u, cmd2->getMinDocCount());

    SplitBucketReply::SP reply(new SplitBucketReply(*cmd2));
    reply->getSplitInfo().push_back(SplitBucketReply::Entry(
            document::BucketId(17, 0), BucketInfo(100, 1000, 10000, true, true)));
    reply->getSplitInfo().push_back(SplitBucketReply::Entry(
            document::BucketId(17, 1), BucketInfo(101, 1001, 10001, true, true)));
    SplitBucketReply::SP reply2(copyReply(reply));

    CPPUNIT_ASSERT_EQUAL(bucketId, reply2->getBucketId());
    CPPUNIT_ASSERT_EQUAL(size_t(2), reply2->getSplitInfo().size());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(17, 0),
                         reply2->getSplitInfo()[0].first);
    CPPUNIT_ASSERT_EQUAL(document::BucketId(17, 1),
                         reply2->getSplitInfo()[1].first);
    CPPUNIT_ASSERT_EQUAL(BucketInfo(100, 1000, 10000, true, true),
                         reply2->getSplitInfo()[0].second);
    CPPUNIT_ASSERT_EQUAL(BucketInfo(101, 1001, 10001, true, true),
                         reply2->getSplitInfo()[1].second);

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testJoinBuckets51()
{
    ScopedName test("testJoinBuckets51");
    document::BucketId bucketId(16, 0);
    document::Bucket bucket(makeDocumentBucket(bucketId));
    std::vector<document::BucketId> sources;
    sources.push_back(document::BucketId(17, 0));
    sources.push_back(document::BucketId(17, 1));
    JoinBucketsCommand::SP cmd(new JoinBucketsCommand(bucket));
    cmd->getSourceBuckets() = sources;
    cmd->setMinJoinBits(3);
    JoinBucketsCommand::SP cmd2(copyCommand(cmd, _version5_1));

    JoinBucketsReply::SP reply(new JoinBucketsReply(*cmd2));
    reply->setBucketInfo(BucketInfo(3,4,5));
    JoinBucketsReply::SP reply2(copyReply(reply));

    CPPUNIT_ASSERT_EQUAL(sources, reply2->getSourceBuckets());
    CPPUNIT_ASSERT_EQUAL(3, (int)cmd2->getMinJoinBits());
    CPPUNIT_ASSERT_EQUAL(BucketInfo(3,4,5), reply2->getBucketInfo());
    CPPUNIT_ASSERT_EQUAL(bucketId, reply2->getBucketId());

    recordOutput(*cmd2);
    recordOutput(*reply2);
}

void
StorageProtocolTest::testDestroyVisitor51()
{
    ScopedName test("testDestroyVisitor51");

    DestroyVisitorCommand::SP cmd(
            new DestroyVisitorCommand("instance"));
    DestroyVisitorCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(string("instance"), cmd2->getInstanceId());

    DestroyVisitorReply::SP reply(new DestroyVisitorReply(*cmd2));
    DestroyVisitorReply::SP reply2(copyReply(reply));

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testRemoveLocation51()
{
    ScopedName test("testRemoveLocation51");
    document::BucketId bucketId(16, 1234);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    RemoveLocationCommand::SP cmd(
            new RemoveLocationCommand("id.group == \"mygroup\"", bucket));
    RemoveLocationCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(vespalib::string("id.group == \"mygroup\""), cmd2->getDocumentSelection());
    CPPUNIT_ASSERT_EQUAL(bucketId, cmd2->getBucketId());

    RemoveLocationReply::SP reply(new RemoveLocationReply(*cmd2));
    RemoveLocationReply::SP reply2(copyReply(reply));

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testCreateVisitor51()
{
    ScopedName test("testCreateVisitor51");

    std::vector<document::BucketId> buckets;
    buckets.push_back(document::BucketId(16, 1));
    buckets.push_back(document::BucketId(16, 2));

    CreateVisitorCommand::SP cmd(
            new CreateVisitorCommand(makeBucketSpace(), "library", "id", "doc selection"));
    cmd->setControlDestination("controldest");
    cmd->setDataDestination("datadest");
    cmd->setVisitorCmdId(1);
    cmd->getParameters().set("one ring", "to rule them all");
    cmd->getParameters().set("one ring to", "find them and");
    cmd->getParameters().set("into darkness", "bind them");
    cmd->setMaximumPendingReplyCount(2);
    cmd->setFromTime(123);
    cmd->setToTime(456);
    cmd->getBuckets() = buckets;
    cmd->setFieldSet("foo,bar,vekterli");
    cmd->setVisitInconsistentBuckets();
    cmd->setQueueTimeout(100);
    cmd->setVisitorOrdering(document::OrderingSpecification::DESCENDING);
    cmd->setPriority(149);
    CreateVisitorCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(string("library"), cmd2->getLibraryName());
    CPPUNIT_ASSERT_EQUAL(string("id"), cmd2->getInstanceId());
    CPPUNIT_ASSERT_EQUAL(string("doc selection"),
                         cmd2->getDocumentSelection());
    CPPUNIT_ASSERT_EQUAL(string("controldest"),
                         cmd2->getControlDestination());
    CPPUNIT_ASSERT_EQUAL(string("datadest"), cmd2->getDataDestination());
    CPPUNIT_ASSERT_EQUAL(api::Timestamp(123), cmd2->getFromTime());
    CPPUNIT_ASSERT_EQUAL(api::Timestamp(456), cmd2->getToTime());
    CPPUNIT_ASSERT_EQUAL(2u, cmd2->getMaximumPendingReplyCount());
    CPPUNIT_ASSERT_EQUAL(buckets, cmd2->getBuckets());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo,bar,vekterli"), cmd2->getFieldSet());
    CPPUNIT_ASSERT(cmd2->visitInconsistentBuckets());
    CPPUNIT_ASSERT_EQUAL(document::OrderingSpecification::DESCENDING, cmd2->getVisitorOrdering());
    CPPUNIT_ASSERT_EQUAL(149, (int)cmd2->getPriority());

    CreateVisitorReply::SP reply(new CreateVisitorReply(*cmd2));
    CreateVisitorReply::SP reply2(copyReply(reply));

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testGetBucketDiff51()
{
    ScopedName test("testGetBucketDiff51");
    document::BucketId bucketId(623);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(4);
    nodes.push_back(13);
    std::vector<GetBucketDiffCommand::Entry> entries;
    entries.push_back(GetBucketDiffCommand::Entry());
    entries.back()._gid = document::GlobalId("1234567890abcdef");
    entries.back()._timestamp = 123456;
    entries.back()._headerSize = 100;
    entries.back()._bodySize = 65536;
    entries.back()._flags = 1;
    entries.back()._hasMask = 3;

    CPPUNIT_ASSERT_EQUAL(std::string(
            "Entry(timestamp: 123456, gid(0x313233343536373839306162), "
                                                            "hasMask: 0x3,\n"
            "      header size: 100, body size: 65536, flags 0x1)"),
        entries.back().toString(true));

    GetBucketDiffCommand::SP cmd(new GetBucketDiffCommand(bucket, nodes, 1056));
    cmd->getDiff() = entries;
    GetBucketDiffCommand::SP cmd2(copyCommand(cmd, _version5_1));

    GetBucketDiffReply::SP reply(new GetBucketDiffReply(*cmd2));
    CPPUNIT_ASSERT_EQUAL(entries, reply->getDiff());
    GetBucketDiffReply::SP reply2(copyReply(reply));

    CPPUNIT_ASSERT_EQUAL(nodes, reply2->getNodes());
    CPPUNIT_ASSERT_EQUAL(entries, reply2->getDiff());
    CPPUNIT_ASSERT_EQUAL(Timestamp(1056), reply2->getMaxTimestamp());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testApplyBucketDiff51()
{
    ScopedName test("testApplyBucketDiff51");
    document::BucketId bucketId(16, 623);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(4);
    nodes.push_back(13);
    std::vector<ApplyBucketDiffCommand::Entry> entries;
    entries.push_back(ApplyBucketDiffCommand::Entry());

    ApplyBucketDiffCommand::SP cmd(new ApplyBucketDiffCommand(bucket, nodes, 1234));
    cmd->getDiff() = entries;
    ApplyBucketDiffCommand::SP cmd2(copyCommand(cmd, _version5_1));

    ApplyBucketDiffReply::SP reply(new ApplyBucketDiffReply(*cmd2));
    ApplyBucketDiffReply::SP reply2(copyReply(reply));

    CPPUNIT_ASSERT_EQUAL(nodes, reply2->getNodes());
    CPPUNIT_ASSERT_EQUAL(entries, reply2->getDiff());
    CPPUNIT_ASSERT_EQUAL(1234u, reply2->getMaxBufferSize());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

void
StorageProtocolTest::testBatchPutRemove51()
{
    ScopedName test("testBatchPutRemove51");

    document::BucketId bucketId(20, 0xf1f1f1f1f1ull);
    document::Bucket bucket(makeDocumentBucket(bucketId));
    BatchPutRemoveCommand::SP cmd(new BatchPutRemoveCommand(bucket));
    cmd->addPut(_testDoc, 100);
    cmd->addHeaderUpdate(_testDoc, 101, 1234);
    cmd->addRemove(_testDoc->getId(), 102);
    cmd->forceMsgId(556677);
    BatchPutRemoveCommand::SP cmd2(copyCommand(cmd, _version5_1));
    CPPUNIT_ASSERT_EQUAL(bucketId, cmd2->getBucketId());
    CPPUNIT_ASSERT_EQUAL(3, (int)cmd2->getOperationCount());
    CPPUNIT_ASSERT_EQUAL(*_testDoc, *(dynamic_cast<const BatchPutRemoveCommand::PutOperation&>(cmd2->getOperation(0)).document));
    CPPUNIT_ASSERT_EQUAL((uint64_t)100, cmd2->getOperation(0).timestamp);
    {
        vespalib::nbostream header;
        _testDoc->serializeHeader(header);
        document::Document headerDoc(_docMan.getTypeRepo(), header);
        CPPUNIT_ASSERT_EQUAL(
                headerDoc,
                *(dynamic_cast<const BatchPutRemoveCommand::HeaderUpdateOperation&>(
                        cmd2->getOperation(1)).document));
    }
    CPPUNIT_ASSERT_EQUAL((uint64_t)101, cmd2->getOperation(1).timestamp);
    CPPUNIT_ASSERT_EQUAL(1234, (int)dynamic_cast<const BatchPutRemoveCommand::HeaderUpdateOperation&>(cmd2->getOperation(1)).timestampToUpdate);
    CPPUNIT_ASSERT_EQUAL(_testDoc->getId(), dynamic_cast<const BatchPutRemoveCommand::RemoveOperation&>(cmd2->getOperation(2)).documentId);
    CPPUNIT_ASSERT_EQUAL((uint64_t)102, cmd2->getOperation(2).timestamp);
    CPPUNIT_ASSERT_EQUAL(uint64_t(556677), cmd2->getMsgId());

    BatchPutRemoveReply::SP reply(new BatchPutRemoveReply(*cmd2));
    reply->getDocumentsNotFound().push_back(document::DocumentId("userdoc:footype:1234:foo1"));
    reply->getDocumentsNotFound().push_back(document::DocumentId("userdoc:footype:1234:foo2"));
    reply->getDocumentsNotFound().push_back(document::DocumentId("userdoc:footype:1234:foo3"));

    BatchPutRemoveReply::SP reply2(copyReply(reply));

    CPPUNIT_ASSERT_EQUAL(3, (int)reply2->getDocumentsNotFound().size());
    CPPUNIT_ASSERT_EQUAL(document::DocumentId("userdoc:footype:1234:foo1"), reply2->getDocumentsNotFound()[0]);
    CPPUNIT_ASSERT_EQUAL(document::DocumentId("userdoc:footype:1234:foo2"), reply2->getDocumentsNotFound()[1]);
    CPPUNIT_ASSERT_EQUAL(document::DocumentId("userdoc:footype:1234:foo3"), reply2->getDocumentsNotFound()[2]);

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

namespace {
    struct MyCommand : public api::InternalCommand {
        MyCommand() : InternalCommand(101) {}

        api::StorageReply::UP makeReply() override;

        void print(std::ostream& out, bool verbose, const std::string& indent) const override {
            out << "MyCommand()";
            if (verbose) {
                out << " : ";
                InternalCommand::print(out, verbose, indent);
            }
        }
    };

    struct MyReply : public api::InternalReply {
        MyReply(const MyCommand& cmd) : InternalReply(102, cmd) {}

        void print(std::ostream& out, bool verbose, const std::string& indent) const override {
            out << "MyReply()";
            if (verbose) {
                out << " : ";
                InternalReply::print(out, verbose, indent);
            }
        }
    };

    api::StorageReply::UP MyCommand::makeReply() {
        return api::StorageReply::UP(new MyReply(*this));
    }
}

void
StorageProtocolTest::testInternalMessage()
{
    ScopedName test("testInternal51");
    MyCommand cmd;
    MyReply reply(cmd);

    recordOutput(cmd);
    recordOutput(reply);
}

void
StorageProtocolTest::testSetBucketState51()
{
    ScopedName test("testSetBucketState51");
    document::BucketId bucketId(16, 0);
    document::Bucket bucket(makeDocumentBucket(bucketId));
    SetBucketStateCommand::SP cmd(
            new SetBucketStateCommand(bucket, SetBucketStateCommand::ACTIVE));
    SetBucketStateCommand::SP cmd2(copyCommand(cmd, _version5_1));

    SetBucketStateReply::SP reply(new SetBucketStateReply(*cmd2));
    SetBucketStateReply::SP reply2(copyReply(reply));

    CPPUNIT_ASSERT_EQUAL(SetBucketStateCommand::ACTIVE, cmd2->getState());
    CPPUNIT_ASSERT_EQUAL(bucketId, cmd2->getBucketId());
    CPPUNIT_ASSERT_EQUAL(bucketId, reply2->getBucketId());

    recordOutput(*cmd2);
    recordOutput(*reply2);
}

void
StorageProtocolTest::testPutCommand52()
{
    ScopedName test("testPutCommand52");

    PutCommand::SP cmd(new PutCommand(_bucket, _testDoc, 14));
    cmd->setCondition(TestAndSetCondition(CONDITION_STRING));

    PutCommand::SP cmd2(copyCommand(cmd, _version5_2));
    CPPUNIT_ASSERT_EQUAL(cmd->getCondition().getSelection(), cmd2->getCondition().getSelection());
}

void
StorageProtocolTest::testUpdateCommand52()
{
    ScopedName test("testUpdateCommand52");

    document::DocumentUpdate::SP update(new document::DocumentUpdate(_docMan.getTypeRepo(), *_testDoc->getDataType(), _testDoc->getId()));
    UpdateCommand::SP cmd(new UpdateCommand(_bucket, update, 14));
    cmd->setCondition(TestAndSetCondition(CONDITION_STRING));

    UpdateCommand::SP cmd2(copyCommand(cmd, _version5_2));
    CPPUNIT_ASSERT_EQUAL(cmd->getCondition().getSelection(), cmd2->getCondition().getSelection());
}

void
StorageProtocolTest::testRemoveCommand52()
{
    ScopedName test("testRemoveCommand52");

    RemoveCommand::SP cmd(new RemoveCommand(_bucket, _testDocId, 159));
    cmd->setCondition(TestAndSetCondition(CONDITION_STRING));

    RemoveCommand::SP cmd2(copyCommand(cmd, _version5_2));
    CPPUNIT_ASSERT_EQUAL(cmd->getCondition().getSelection(), cmd2->getCondition().getSelection());
}

void
StorageProtocolTest::testPutCommandWithBucketSpace6_0()
{
    ScopedName test("testPutCommandWithBucketSpace6_0");

    document::Bucket bucket(document::BucketSpace(5), _bucket.getBucketId());
    auto cmd = std::make_shared<PutCommand>(bucket, _testDoc, 14);

    auto cmd2 = copyCommand(cmd, _version6_0);
    CPPUNIT_ASSERT_EQUAL(bucket, cmd2->getBucket());
}

void
StorageProtocolTest::testCreateVisitorWithBucketSpace6_0()
{
    ScopedName test("testCreateVisitorWithBucketSpace6_0");

    document::BucketSpace bucketSpace(5);
    auto cmd = std::make_shared<CreateVisitorCommand>(bucketSpace, "library", "id", "doc selection");

    auto cmd2 = copyCommand(cmd, _version6_0);
    CPPUNIT_ASSERT_EQUAL(bucketSpace, cmd2->getBucketSpace());
}

void
StorageProtocolTest::testRequestBucketInfoWithBucketSpace6_0()
{
    ScopedName test("testRequestBucketInfoWithBucketSpace6_0");

    document::BucketSpace bucketSpace(5);
    std::vector<document::BucketId> ids = {document::BucketId(3)};
    auto cmd = std::make_shared<RequestBucketInfoCommand>(bucketSpace, ids);

    auto cmd2 = copyCommand(cmd, _version6_0);
    CPPUNIT_ASSERT_EQUAL(bucketSpace, cmd2->getBucketSpace());
    CPPUNIT_ASSERT_EQUAL(ids, cmd2->getBuckets());
}

void
StorageProtocolTest::serialized_size_is_used_to_set_approx_size_of_storage_message()
{
    ScopedName test("serialized_size_is_used_to_set_approx_size_of_storage_message");

    PutCommand::SP cmd(new PutCommand(_bucket, _testDoc, 14));
    CPPUNIT_ASSERT_EQUAL(50u, cmd->getApproxByteSize());

    PutCommand::SP cmd2(copyCommand(cmd, _version6_0));
    CPPUNIT_ASSERT_EQUAL(181u, cmd2->getApproxByteSize());
}

void
StorageProtocolTest::testStringOutputs()
{
    std::cerr << "\nNon verbose output:\n";
    for (uint32_t i=0, n=_nonVerboseMessageStrings.size(); i<n; ++i) {
        std::cerr << _nonVerboseMessageStrings[i] << "\n";
    }
    std::cerr << "\nVerbose output:\n";
    for (uint32_t i=0, n=_verboseMessageStrings.size(); i<n; ++i) {
        std::cerr << _verboseMessageStrings[i] << "\n";
    }
}

void
StorageProtocolTest::testWriteSerialization50()
{
    std::ofstream of("mbusprot/mbusprot.5.0.serialization.5.1");
    of << std::hex << std::setfill('0');
    for (uint32_t i=0, n=_serialization50.size(); i<n; ++i) {
        char c = _serialization50[i];
        if (c > 126 || (c < 32 && c != 10)) {
            int32_t num = static_cast<int32_t>(c);
            if (num < 0) num += 256;
            of << '\\' << std::setw(2) << num;
        } else if (c == '\\') {
            of << "\\\\";
        } else {
            of << c;
        }
    }
    of.close();
}

} // mbusprot
} // storage
