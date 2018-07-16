// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vector>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/persistence/messages.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <vespa/document/test/make_document_bucket.h>

using document::test::makeDocumentBucket;

namespace storage {

class MergeBlockingTest : public FileStorTestFixture
{
public:
    void setupDisks() {
        FileStorTestFixture::setupPersistenceThreads(1);
        _node->setPersistenceProvider(
                spi::PersistenceProvider::UP(
                        new spi::dummy::DummyPersistence(_node->getTypeRepo(), 1)));
    }

public:
    void testRejectMergeForInconsistentInnerBucket();
    void testRejectMergeForInconsistentLeafBucket();
    void testRejectGetBucketDiffWithInconsistentBucket();
    void testRejectApplyDiffWhenBucketHasBecomeInconsistent();
    void testRejectApplyReplyWhenBucketHasBecomeInconsistent();
    void testRejectGetDiffReplyWhenBucketHasBecomeInconsistent();
    void testRejectMergeWhenLowUsedBitCount();

    void setUp() override;

    CPPUNIT_TEST_SUITE(MergeBlockingTest);
    CPPUNIT_TEST(testRejectMergeForInconsistentInnerBucket);
    CPPUNIT_TEST(testRejectMergeForInconsistentLeafBucket);
    CPPUNIT_TEST(testRejectGetBucketDiffWithInconsistentBucket);
    CPPUNIT_TEST(testRejectApplyDiffWhenBucketHasBecomeInconsistent);
    CPPUNIT_TEST(testRejectApplyReplyWhenBucketHasBecomeInconsistent);
    CPPUNIT_TEST(testRejectGetDiffReplyWhenBucketHasBecomeInconsistent);
    CPPUNIT_TEST(testRejectMergeWhenLowUsedBitCount);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MergeBlockingTest);

void
MergeBlockingTest::setUp()
{
    setupDisks();
}

namespace {

api::StorageMessageAddress
makeAddress() {
    return api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 0);
}

void
assignCommandMeta(api::StorageCommand& msg) {
    msg.setAddress(makeAddress());
    msg.setSourceIndex(0);
}

std::vector<api::MergeBucketCommand::Node>
getNodes() {
    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    return nodes;
}

std::vector<api::MergeBucketCommand::Node>
getNodesWithForwarding() {
    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    nodes.push_back(2);
    return nodes;
}

std::shared_ptr<api::MergeBucketCommand>
createMerge(const document::BucketId& bucket) {
    std::shared_ptr<api::MergeBucketCommand> cmd(
            new api::MergeBucketCommand(makeDocumentBucket(bucket), getNodes(), api::Timestamp(1000)));
    assignCommandMeta(*cmd);
    return cmd;
}

std::shared_ptr<api::GetBucketDiffCommand>
createGetDiff(const document::BucketId& bucket,
              const std::vector<api::MergeBucketCommand::Node>& nodes)
{
    std::shared_ptr<api::GetBucketDiffCommand> cmd(
            new api::GetBucketDiffCommand(makeDocumentBucket(bucket), nodes, api::Timestamp(1000)));
    assignCommandMeta(*cmd);
    return cmd;
}

std::shared_ptr<api::ApplyBucketDiffCommand>
createApplyDiff(const document::BucketId& bucket,
                const std::vector<api::MergeBucketCommand::Node>& nodes) {
    std::shared_ptr<api::ApplyBucketDiffCommand> cmd(
            new api::ApplyBucketDiffCommand(makeDocumentBucket(bucket), nodes, 1024*1024));
    assignCommandMeta(*cmd);
    return cmd;
}

const document::BucketId leafBucket(17, 1);
const document::BucketId innerBucket(16, 1);
const document::BucketId innerBucket2(15, 1);

}

void
MergeBlockingTest::testRejectMergeForInconsistentInnerBucket()
{
    TestFileStorComponents c(*this, "testRejectMergeForInconsistentInnerBucket");
    createBucket(leafBucket);

    std::shared_ptr<api::MergeBucketCommand> cmd(createMerge(innerBucket));
    c.top.sendDown(cmd);

    expectAbortedReply<api::MergeBucketReply>(c.top);
    CPPUNIT_ASSERT(!bucketExistsInDb(innerBucket));
}

void
MergeBlockingTest::testRejectMergeForInconsistentLeafBucket()
{
    TestFileStorComponents c(*this, "testRejectMergeForInconsistentInnerBucket");
    createBucket(innerBucket);

    std::shared_ptr<api::MergeBucketCommand> cmd(createMerge(leafBucket));
    c.top.sendDown(cmd);

    expectAbortedReply<api::MergeBucketReply>(c.top);
    CPPUNIT_ASSERT(!bucketExistsInDb(leafBucket));
}

void
MergeBlockingTest::testRejectGetBucketDiffWithInconsistentBucket()
{
    TestFileStorComponents c(*this, "testRejectGetBucketDiffWithInconsistentBucket");
    CPPUNIT_ASSERT(innerBucket.contains(leafBucket));
    createBucket(innerBucket);

    std::shared_ptr<api::GetBucketDiffCommand> cmd(createGetDiff(leafBucket, getNodes()));
    c.top.sendDown(cmd);

    expectAbortedReply<api::GetBucketDiffReply>(c.top);
    CPPUNIT_ASSERT(!bucketExistsInDb(leafBucket));
}

void
MergeBlockingTest::testRejectApplyDiffWhenBucketHasBecomeInconsistent()
{
    TestFileStorComponents c(*this, "testRejectApplyDiffWhenBucketHasBecomeInconsistent");
    createBucket(leafBucket);
    createBucket(innerBucket);

    std::shared_ptr<api::ApplyBucketDiffCommand> applyDiff(
            createApplyDiff(innerBucket, getNodes()));
    c.top.sendDown(applyDiff);

    expectAbortedReply<api::ApplyBucketDiffReply>(c.top);
}

void
MergeBlockingTest::testRejectApplyReplyWhenBucketHasBecomeInconsistent()
{
    TestFileStorComponents c(*this, "testRejectApplyReplyWhenBucketHasBecomeInconsistent");
    createBucket(innerBucket);

    std::shared_ptr<api::ApplyBucketDiffCommand> applyDiff(
            createApplyDiff(innerBucket, getNodesWithForwarding()));
    c.top.sendDown(applyDiff);
    c.top.waitForMessages(1, MSG_WAIT_TIME);

    api::StorageMessage::SP fwdDiff(
            c.top.getAndRemoveMessage(api::MessageType::APPLYBUCKETDIFF));
    api::ApplyBucketDiffCommand& diffCmd(
            dynamic_cast<api::ApplyBucketDiffCommand&>(*fwdDiff));

    api::ApplyBucketDiffReply::SP diffReply(
            new api::ApplyBucketDiffReply(diffCmd));
    createBucket(leafBucket);
    c.top.sendDown(diffReply);

    expectAbortedReply<api::ApplyBucketDiffReply>(c.top);
}

void
MergeBlockingTest::testRejectGetDiffReplyWhenBucketHasBecomeInconsistent()
{
    TestFileStorComponents c(*this, "testRejectGetDiffReplyWhenBucketHasBecomeInconsistent");
    createBucket(innerBucket);

    std::shared_ptr<api::GetBucketDiffCommand> getDiff(
            createGetDiff(innerBucket, getNodesWithForwarding()));
    c.top.sendDown(getDiff);
    c.top.waitForMessages(1, MSG_WAIT_TIME);

    api::StorageMessage::SP fwdDiff(
            c.top.getAndRemoveMessage(api::MessageType::GETBUCKETDIFF));
    api::GetBucketDiffCommand& diffCmd(
            dynamic_cast<api::GetBucketDiffCommand&>(*fwdDiff));

    api::GetBucketDiffReply::SP diffReply(
            new api::GetBucketDiffReply(diffCmd));
    createBucket(innerBucket2);
    c.top.sendDown(diffReply);

    expectAbortedReply<api::GetBucketDiffReply>(c.top);
}

/**
 * Test case for buckets in ticket 6389558, comment #4.
 */
void
MergeBlockingTest::testRejectMergeWhenLowUsedBitCount()
{
    document::BucketId superBucket(1, 0x1);
    document::BucketId subBucket(2, 0x1);

    CPPUNIT_ASSERT(superBucket.contains(subBucket));

    TestFileStorComponents c(*this, "testRejectMergeWithInconsistentBucket");
    createBucket(superBucket);

    std::shared_ptr<api::MergeBucketCommand> cmd(createMerge(subBucket));
    c.top.sendDown(cmd);

    expectAbortedReply<api::MergeBucketReply>(c.top);
    CPPUNIT_ASSERT(!bucketExistsInDb(subBucket));
}

} // ns storage
