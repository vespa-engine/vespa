// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/internal.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/mbusprot/storageprotocol.h>
#include <vespa/storageapi/mbusprot/storagecommand.h>
#include <vespa/storageapi/mbusprot/storagereply.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/document.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <iomanip>
#include <sstream>

#include <gtest/gtest.h>

using namespace ::testing;

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

namespace vespalib {

// Needed for GTest to properly understand how to print Version values.
void PrintTo(const vespalib::Version& v, std::ostream* os) {
    *os << v.toString();
}

}

namespace storage::api {

struct StorageProtocolTest : TestWithParam<vespalib::Version> {
    document::TestDocMan _docMan;
    document::Document::SP _testDoc;
    document::DocumentId _testDocId;
    document::Bucket  _bucket;
    vespalib::Version _version5_0{5, 0, 12};
    vespalib::Version _version5_1{5, 1, 0};
    vespalib::Version _version5_2{5, 93, 30};
    vespalib::Version _version6_0{6, 240, 0};
    vespalib::Version _version7_0{7, 0, 0}; // FIXME
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
          _protocol(_docMan.getTypeRepoSP(), _loadTypes)
    {
        _loadTypes.addLoadType(34, "foo", documentapi::Priority::PRI_NORMAL_2);
    }
    ~StorageProtocolTest();

    template<typename Command>
    std::shared_ptr<Command> copyCommand(const std::shared_ptr<Command>&);
    template<typename Reply>
    std::shared_ptr<Reply> copyReply(const std::shared_ptr<Reply>&);
    void recordOutput(const api::StorageMessage& msg);

    void recordSerialization50();
};

StorageProtocolTest::~StorageProtocolTest() = default;

namespace {

std::string version_as_gtest_string(TestParamInfo<vespalib::Version> info) {
    std::ostringstream ss;
    auto& p = info.param;
    // Dots are not allowed in test names, so convert to underscores.
    ss << p.getMajor() << '_' << p.getMinor() << '_' << p.getMicro();
    return ss.str();
}

}

// TODO replace with INSTANTIATE_TEST_SUITE_P on newer gtest versions
INSTANTIATE_TEST_CASE_P(MultiVersionTest, StorageProtocolTest,
                        Values(vespalib::Version(6, 240, 0),
                               vespalib::Version(7, 0, 0)), // TODO proper 7 version
                        version_as_gtest_string);

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
    mbus::Message::UP lastCommand;
    mbus::Reply::UP lastReply;
}

TEST_F(StorageProtocolTest, testAddress50) {
    StorageMessageAddress address("foo", lib::NodeType::STORAGE, 3);
    EXPECT_EQ(vespalib::string("storage/cluster.foo/storage/3/default"),
                         address.getRoute().toString());
}

template<typename Command> std::shared_ptr<Command>
StorageProtocolTest::copyCommand(const std::shared_ptr<Command>& m)
{
    auto mbusMessage = std::make_unique<mbusprot::StorageCommand>(m);
    auto version = GetParam();
    mbus::Blob blob = _protocol.encode(version, *mbusMessage);
    mbus::Routable::UP copy(_protocol.decode(version, blob));
    assert(copy.get());

    auto* copy2 = dynamic_cast<mbusprot::StorageCommand*>(copy.get());
    assert(copy2 != nullptr);

    StorageCommand::SP internalMessage(copy2->getCommand());
    lastCommand = std::move(mbusMessage);

    return std::dynamic_pointer_cast<Command>(internalMessage);
}

template<typename Reply> std::shared_ptr<Reply>
StorageProtocolTest::copyReply(const std::shared_ptr<Reply>& m)
{
    auto mbusMessage = std::make_unique<mbusprot::StorageReply>(m);
    auto version = GetParam();
    mbus::Blob blob = _protocol.encode(version, *mbusMessage);
    mbus::Routable::UP copy(_protocol.decode(version, blob));
    assert(copy.get());

    auto* copy2 = dynamic_cast<mbusprot::StorageReply*>(copy.get());
    assert(copy2 != nullptr);

    copy2->setMessage(std::move(lastCommand));
    auto internalMessage = copy2->getReply();
    lastReply = std::move(mbusMessage);
    lastCommand = copy2->getMessage();
    return std::dynamic_pointer_cast<Reply>(internalMessage);
}

// TODO remove whatever this thing might be
void StorageProtocolTest::recordSerialization50() {
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

TEST_P(StorageProtocolTest, testPut) {
    auto cmd = std::make_shared<PutCommand>(_bucket, _testDoc, 14);
    cmd->setUpdateTimestamp(Timestamp(13));
    cmd->setLoadType(_loadTypes["foo"]);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(*_testDoc, *cmd2->getDocument());
    EXPECT_EQ(vespalib::string("foo"), cmd2->getLoadType().getName());
    EXPECT_EQ(Timestamp(14), cmd2->getTimestamp());
    EXPECT_EQ(Timestamp(13), cmd2->getUpdateTimestamp());

    auto reply = std::make_shared<PutReply>(*cmd2);
    ASSERT_TRUE(reply->hasDocument());
    EXPECT_EQ(*_testDoc, *reply->getDocument());
    reply->setBucketInfo(BucketInfo(1,2,3,4,5, true, false, 48));
    auto reply2 = copyReply(reply);
    ASSERT_TRUE(reply2->hasDocument());
    EXPECT_EQ(*_testDoc, *reply->getDocument());
    EXPECT_EQ(_testDoc->getId(), reply2->getDocumentId());
    EXPECT_EQ(Timestamp(14), reply2->getTimestamp());
    EXPECT_EQ(BucketInfo(1,2,3,4,5, true, false, 48), reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testUpdate) {
    auto update = std::make_shared<document::DocumentUpdate>(
            _docMan.getTypeRepo(), *_testDoc->getDataType(), _testDoc->getId());
    auto assignUpdate = std::make_shared<document::AssignValueUpdate>(document::IntFieldValue(17));
    document::FieldUpdate fieldUpdate(_testDoc->getField("headerval"));
    fieldUpdate.addUpdate(*assignUpdate);
    update->addUpdate(fieldUpdate);

    update->addFieldPathUpdate(document::FieldPathUpdate::CP(
                    new document::RemoveFieldPathUpdate("headerval", "testdoctype1.headerval > 0")));

    auto cmd = std::make_shared<UpdateCommand>(_bucket, update, 14);
    EXPECT_EQ(Timestamp(0), cmd->getOldTimestamp());
    cmd->setOldTimestamp(10);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(_testDocId, cmd2->getDocumentId());
    EXPECT_EQ(Timestamp(14), cmd2->getTimestamp());
    EXPECT_EQ(Timestamp(10), cmd2->getOldTimestamp());
    EXPECT_EQ(*update, *cmd2->getUpdate());

    auto reply = std::make_shared<UpdateReply>(*cmd2, 8);
    reply->setBucketInfo(BucketInfo(1,2,3,4,5, true, false, 48));
    auto reply2 = copyReply(reply);
    EXPECT_EQ(_testDocId, reply2->getDocumentId());
    EXPECT_EQ(Timestamp(14), reply2->getTimestamp());
    EXPECT_EQ(Timestamp(8), reply->getOldTimestamp());
    EXPECT_EQ(BucketInfo(1,2,3,4,5, true, false, 48), reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testGet) {
    auto cmd = std::make_shared<GetCommand>(_bucket, _testDocId, "foo,bar,vekterli", 123);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(_testDocId, cmd2->getDocumentId());
    EXPECT_EQ(Timestamp(123), cmd2->getBeforeTimestamp());
    EXPECT_EQ(vespalib::string("foo,bar,vekterli"), cmd2->getFieldSet());

    auto reply = std::make_shared<GetReply>(*cmd2, _testDoc, 100);
    reply->setBucketInfo(BucketInfo(1,2,3,4,5, true, false, 48));
    auto reply2 = copyReply(reply);
    ASSERT_TRUE(reply2.get() != nullptr);
    ASSERT_TRUE(reply2->getDocument().get() != nullptr);
    EXPECT_EQ(*_testDoc, *reply2->getDocument());
    EXPECT_EQ(_testDoc->getId(), reply2->getDocumentId());
    EXPECT_EQ(Timestamp(123), reply2->getBeforeTimestamp());
    EXPECT_EQ(Timestamp(100), reply2->getLastModifiedTimestamp());
    EXPECT_EQ(BucketInfo(1,2,3,4,5, true, false, 48), reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testRemove) {
    auto cmd = std::make_shared<RemoveCommand>(_bucket, _testDocId, 159);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(_testDocId, cmd2->getDocumentId());
    EXPECT_EQ(Timestamp(159), cmd2->getTimestamp());

    auto reply = std::make_shared<RemoveReply>(*cmd2, 48);
    reply->setBucketInfo(BucketInfo(1,2,3,4,5, true, false, 48));

    auto reply2 = copyReply(reply);
    EXPECT_EQ(_testDocId, reply2->getDocumentId());
    EXPECT_EQ(Timestamp(159), reply2->getTimestamp());
    EXPECT_EQ(Timestamp(48), reply2->getOldTimestamp());
    EXPECT_EQ(BucketInfo(1,2,3,4,5, true, false, 48), reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testRevert) {
    std::vector<Timestamp> tokens;
    tokens.push_back(59);
    auto cmd = std::make_shared<RevertCommand>(_bucket, tokens);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(tokens, cmd2->getRevertTokens());

    auto reply = std::make_shared<RevertReply>(*cmd2);
    BucketInfo info(0x12345432, 101, 520);
    reply->setBucketInfo(info);
    auto reply2 = copyReply(reply);

    EXPECT_EQ(info, reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testRequestBucketInfo) {
    {
        std::vector<document::BucketId> ids;
        ids.push_back(document::BucketId(3));
        ids.push_back(document::BucketId(7));
        auto cmd = std::make_shared<RequestBucketInfoCommand>(makeBucketSpace(), ids);
        auto cmd2 = copyCommand(cmd);
        EXPECT_EQ(ids, cmd2->getBuckets());
        EXPECT_FALSE(cmd2->hasSystemState());

        recordOutput(*cmd2);
    }
    {
        ClusterState state("distributor:3 .1.s:d");
        auto cmd = std::make_shared<RequestBucketInfoCommand>(makeBucketSpace(), 3, state, "14");
        auto cmd2 = copyCommand(cmd);
        ASSERT_TRUE(cmd2->hasSystemState());
        EXPECT_EQ(uint16_t(3), cmd2->getDistributor());
        EXPECT_EQ(state, cmd2->getSystemState());
        EXPECT_EQ(size_t(0), cmd2->getBuckets().size());

        auto reply = std::make_shared<RequestBucketInfoReply>(*cmd);
        RequestBucketInfoReply::Entry e;
        e._bucketId = document::BucketId(4);
        const uint64_t lastMod = 0x1337cafe98765432ULL;
        e._info = BucketInfo(43, 24, 123, 44, 124, false, true, lastMod);
        reply->getBucketInfo().push_back(e);
        auto reply2 = copyReply(reply);
        EXPECT_EQ(size_t(1), reply2->getBucketInfo().size());
        auto& entries(reply2->getBucketInfo());
        EXPECT_EQ(e, entries[0]);
        // "Last modified" not counted by operator== for some reason. Testing
        // separately until we can figure out if this is by design or not.
        EXPECT_EQ(lastMod, entries[0]._info.getLastModified());

        recordOutput(*cmd2);
        recordOutput(*reply2);
        recordSerialization50();
    }
}

TEST_P(StorageProtocolTest, testNotifyBucketChange) {
    BucketInfo info(2, 3, 4);
    document::BucketId modifiedBucketId(20, 1000);
    document::Bucket modifiedBucket(makeDocumentBucket(modifiedBucketId));
    auto cmd = std::make_shared<NotifyBucketChangeCommand>(modifiedBucket, info);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(document::BucketId(20, 1000), cmd2->getBucketId());
    EXPECT_EQ(info, cmd2->getBucketInfo());

    auto reply = std::make_shared<NotifyBucketChangeReply>(*cmd);
    auto reply2 = copyReply(reply);

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testCreateBucket) {
    document::BucketId bucketId(623);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    auto cmd = std::make_shared<CreateBucketCommand>(bucket);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(bucketId, cmd2->getBucketId());

    auto reply = std::make_shared<CreateBucketReply>(*cmd);
    auto reply2 = copyReply(reply);
    EXPECT_EQ(bucketId, reply2->getBucketId());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testDeleteBucket) {
    document::BucketId bucketId(623);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    auto cmd = std::make_shared<DeleteBucketCommand>(bucket);
    BucketInfo info(0x100, 200, 300);
    cmd->setBucketInfo(info);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(bucketId, cmd2->getBucketId());
    EXPECT_EQ(info, cmd2->getBucketInfo());

    auto reply = std::make_shared<DeleteBucketReply>(*cmd);
    // Not set automatically by constructor
    reply->setBucketInfo(cmd2->getBucketInfo());
    auto reply2 = copyReply(reply);
    EXPECT_EQ(bucketId, reply2->getBucketId());
    EXPECT_EQ(info, reply2->getBucketInfo());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testMergeBucket) {
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

    auto cmd = std::make_shared<MergeBucketCommand>(bucket, nodes, Timestamp(1234), 567, chain);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(bucketId, cmd2->getBucketId());
    EXPECT_EQ(nodes, cmd2->getNodes());
    EXPECT_EQ(Timestamp(1234), cmd2->getMaxTimestamp());
    EXPECT_EQ(uint32_t(567), cmd2->getClusterStateVersion());
    EXPECT_EQ(chain, cmd2->getChain());

    auto reply = std::make_shared<MergeBucketReply>(*cmd);
    auto reply2 = copyReply(reply);
    EXPECT_EQ(bucketId, reply2->getBucketId());
    EXPECT_EQ(nodes, reply2->getNodes());
    EXPECT_EQ(Timestamp(1234), reply2->getMaxTimestamp());
    EXPECT_EQ(uint32_t(567), reply2->getClusterStateVersion());
    EXPECT_EQ(chain, reply2->getChain());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testSplitBucket) {
    document::BucketId bucketId(16, 0);
    document::Bucket bucket(makeDocumentBucket(bucketId));
    auto cmd = std::make_shared<SplitBucketCommand>(bucket);
    EXPECT_EQ(0u, cmd->getMinSplitBits());
    EXPECT_EQ(58u, cmd->getMaxSplitBits());
    EXPECT_EQ(std::numeric_limits<uint32_t>().max(), cmd->getMinByteSize());
    EXPECT_EQ(std::numeric_limits<uint32_t>().max(), cmd->getMinDocCount());
    cmd->setMinByteSize(1000);
    cmd->setMinDocCount(5);
    cmd->setMaxSplitBits(40);
    cmd->setMinSplitBits(20);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(20u, cmd2->getMinSplitBits());
    EXPECT_EQ(40u, cmd2->getMaxSplitBits());
    EXPECT_EQ(1000u, cmd2->getMinByteSize());
    EXPECT_EQ(5u, cmd2->getMinDocCount());

    auto reply = std::make_shared<SplitBucketReply>(*cmd2);
    reply->getSplitInfo().emplace_back(document::BucketId(17, 0), BucketInfo(100, 1000, 10000, true, true));
    reply->getSplitInfo().emplace_back(document::BucketId(17, 1), BucketInfo(101, 1001, 10001, true, true));
    auto reply2 = copyReply(reply);

    EXPECT_EQ(bucketId, reply2->getBucketId());
    EXPECT_EQ(size_t(2), reply2->getSplitInfo().size());
    EXPECT_EQ(document::BucketId(17, 0), reply2->getSplitInfo()[0].first);
    EXPECT_EQ(document::BucketId(17, 1), reply2->getSplitInfo()[1].first);
    EXPECT_EQ(BucketInfo(100, 1000, 10000, true, true), reply2->getSplitInfo()[0].second);
    EXPECT_EQ(BucketInfo(101, 1001, 10001, true, true), reply2->getSplitInfo()[1].second);

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testJoinBuckets) {
    document::BucketId bucketId(16, 0);
    document::Bucket bucket(makeDocumentBucket(bucketId));
    std::vector<document::BucketId> sources;
    sources.push_back(document::BucketId(17, 0));
    sources.push_back(document::BucketId(17, 1));
    auto cmd = std::make_shared<JoinBucketsCommand>(bucket);
    cmd->getSourceBuckets() = sources;
    cmd->setMinJoinBits(3);
    auto cmd2 = copyCommand(cmd);

    auto reply = std::make_shared<JoinBucketsReply>(*cmd2);
    reply->setBucketInfo(BucketInfo(3,4,5));
    auto reply2 = copyReply(reply);

    EXPECT_EQ(sources, reply2->getSourceBuckets());
    EXPECT_EQ(3, cmd2->getMinJoinBits());
    EXPECT_EQ(BucketInfo(3,4,5), reply2->getBucketInfo());
    EXPECT_EQ(bucketId, reply2->getBucketId());

    recordOutput(*cmd2);
    recordOutput(*reply2);
}

TEST_P(StorageProtocolTest, testDestroyVisitor) {
    auto cmd = std::make_shared<DestroyVisitorCommand>("instance");
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ("instance", cmd2->getInstanceId());

    auto reply = std::make_shared<DestroyVisitorReply>(*cmd2);
    auto reply2 = copyReply(reply);

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testRemoveLocation) {
    document::BucketId bucketId(16, 1234);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    auto cmd = std::make_shared<RemoveLocationCommand>("id.group == \"mygroup\"", bucket);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ("id.group == \"mygroup\"", cmd2->getDocumentSelection());
    EXPECT_EQ(bucketId, cmd2->getBucketId());

    auto reply = std::make_shared<RemoveLocationReply>(*cmd2);
    auto reply2 = copyReply(reply);

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testCreateVisitor) {
    std::vector<document::BucketId> buckets;
    buckets.push_back(document::BucketId(16, 1));
    buckets.push_back(document::BucketId(16, 2));

    auto cmd = std::make_shared<CreateVisitorCommand>(makeBucketSpace(), "library", "id", "doc selection");
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
    cmd->setPriority(149);
    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ("library", cmd2->getLibraryName());
    EXPECT_EQ("id", cmd2->getInstanceId());
    EXPECT_EQ("doc selection", cmd2->getDocumentSelection());
    EXPECT_EQ("controldest", cmd2->getControlDestination());
    EXPECT_EQ("datadest", cmd2->getDataDestination());
    EXPECT_EQ(api::Timestamp(123), cmd2->getFromTime());
    EXPECT_EQ(api::Timestamp(456), cmd2->getToTime());
    EXPECT_EQ(2u, cmd2->getMaximumPendingReplyCount());
    EXPECT_EQ(buckets, cmd2->getBuckets());
    EXPECT_EQ("foo,bar,vekterli", cmd2->getFieldSet());
    EXPECT_TRUE(cmd2->visitInconsistentBuckets());
    EXPECT_EQ(149, cmd2->getPriority());

    auto reply = std::make_shared<CreateVisitorReply>(*cmd2);
    auto reply2 = copyReply(reply);

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

TEST_P(StorageProtocolTest, testGetBucketDiff) {
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

    EXPECT_EQ("Entry(timestamp: 123456, gid(0x313233343536373839306162), hasMask: 0x3,\n"
              "      header size: 100, body size: 65536, flags 0x1)",
              entries.back().toString(true));

    auto cmd = std::make_shared<GetBucketDiffCommand>(bucket, nodes, 1056);
    cmd->getDiff() = entries;
    auto cmd2 = copyCommand(cmd);

    auto reply = std::make_shared<GetBucketDiffReply>(*cmd2);
    EXPECT_EQ(entries, reply->getDiff());
    auto reply2 = copyReply(reply);

    EXPECT_EQ(nodes, reply2->getNodes());
    EXPECT_EQ(entries, reply2->getDiff());
    EXPECT_EQ(Timestamp(1056), reply2->getMaxTimestamp());

    recordOutput(*cmd2);
    recordOutput(*reply2);
    recordSerialization50();
}

namespace {

ApplyBucketDiffCommand::Entry dummy_apply_entry() {
    ApplyBucketDiffCommand::Entry e;
    e._docName = "my cool id";
    vespalib::string header_data = "fancy header";
    e._headerBlob.resize(header_data.size());
    memcpy(&e._headerBlob[0], header_data.data(), header_data.size());

    vespalib::string body_data = "fancier body!";
    e._bodyBlob.resize(body_data.size());
    memcpy(&e._bodyBlob[0], body_data.data(), body_data.size());

    GetBucketDiffCommand::Entry meta;
    meta._timestamp = 567890;
    meta._hasMask = 0x3;
    meta._flags = 0x1;
    meta._headerSize = 12345;
    meta._headerSize = header_data.size();
    meta._bodySize = body_data.size();

    e._entry = meta;
    return e;
}

}

TEST_P(StorageProtocolTest, testApplyBucketDiff) {
    document::BucketId bucketId(16, 623);
    document::Bucket bucket(makeDocumentBucket(bucketId));

    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(4);
    nodes.push_back(13);
    std::vector<ApplyBucketDiffCommand::Entry> entries = {dummy_apply_entry()};

    auto cmd = std::make_shared<ApplyBucketDiffCommand>(bucket, nodes, 1234);
    cmd->getDiff() = entries;
    auto cmd2 = copyCommand(cmd);

    auto reply = std::make_shared<ApplyBucketDiffReply>(*cmd2);
    auto reply2 = copyReply(reply);

    EXPECT_EQ(nodes, reply2->getNodes());
    EXPECT_EQ(entries, reply2->getDiff());
    EXPECT_EQ(1234u, reply2->getMaxBufferSize());

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
        return std::make_unique<MyReply>(*this);
    }
}

TEST_P(StorageProtocolTest, testInternalMessage) {
    MyCommand cmd;
    MyReply reply(cmd);

    recordOutput(cmd);
    recordOutput(reply);
}

TEST_P(StorageProtocolTest, set_bucket_state) {
    document::BucketId bucketId(16, 0);
    document::Bucket bucket(makeDocumentBucket(bucketId));
    auto cmd = std::make_shared<SetBucketStateCommand>(bucket, SetBucketStateCommand::ACTIVE);
    auto cmd2 = copyCommand(cmd);

    auto reply = std::make_shared<SetBucketStateReply>(*cmd2);
    auto reply2 = copyReply(reply);

    EXPECT_EQ(SetBucketStateCommand::ACTIVE, cmd2->getState());
    EXPECT_EQ(bucketId, cmd2->getBucketId());
    EXPECT_EQ(bucketId, reply2->getBucketId());

    recordOutput(*cmd2);
    recordOutput(*reply2);
}

TEST_P(StorageProtocolTest, put_command_with_condition) {
    auto cmd = std::make_shared<PutCommand>(_bucket, _testDoc, 14);
    cmd->setCondition(TestAndSetCondition(CONDITION_STRING));

    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(cmd->getCondition().getSelection(), cmd2->getCondition().getSelection());
}

TEST_P(StorageProtocolTest, update_command_with_condition) {
    auto update = std::make_shared<document::DocumentUpdate>(
            _docMan.getTypeRepo(), *_testDoc->getDataType(), _testDoc->getId());
    auto cmd = std::make_shared<UpdateCommand>(_bucket, update, 14);
    cmd->setCondition(TestAndSetCondition(CONDITION_STRING));

    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(cmd->getCondition().getSelection(), cmd2->getCondition().getSelection());
}

TEST_P(StorageProtocolTest, remove_command_with_condition) {
    auto cmd = std::make_shared<RemoveCommand>(_bucket, _testDocId, 159);
    cmd->setCondition(TestAndSetCondition(CONDITION_STRING));

    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(cmd->getCondition().getSelection(), cmd2->getCondition().getSelection());
}

TEST_P(StorageProtocolTest, testPutCommandWithBucketSpace) {
    document::Bucket bucket(document::BucketSpace(5), _bucket.getBucketId());
    auto cmd = std::make_shared<PutCommand>(bucket, _testDoc, 14);

    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(bucket, cmd2->getBucket());
}

TEST_P(StorageProtocolTest, testCreateVisitorWithBucketSpace) {
    document::BucketSpace bucketSpace(5);
    auto cmd = std::make_shared<CreateVisitorCommand>(bucketSpace, "library", "id", "doc selection");

    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(bucketSpace, cmd2->getBucketSpace());
}

TEST_P(StorageProtocolTest, testRequestBucketInfoWithBucketSpace) {
    document::BucketSpace bucketSpace(5);
    std::vector<document::BucketId> ids = {document::BucketId(3)};
    auto cmd = std::make_shared<RequestBucketInfoCommand>(bucketSpace, ids);

    auto cmd2 = copyCommand(cmd);
    EXPECT_EQ(bucketSpace, cmd2->getBucketSpace());
    EXPECT_EQ(ids, cmd2->getBuckets());
}

TEST_P(StorageProtocolTest, serialized_size_is_used_to_set_approx_size_of_storage_message) {
    auto cmd = std::make_shared<PutCommand>(_bucket, _testDoc, 14);
    EXPECT_EQ(50u, cmd->getApproxByteSize());

    auto cmd2 = copyCommand(cmd);
    auto version = GetParam();
    if (version.getMajor() == 7) { // Protobuf encoding
        EXPECT_EQ(158u, cmd2->getApproxByteSize());
    } else { // Legacy encoding
        EXPECT_EQ(181u, cmd2->getApproxByteSize());
    }
}

} // storage::api
