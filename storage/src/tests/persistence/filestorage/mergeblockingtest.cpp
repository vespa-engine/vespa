// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <vector>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct MergeBlockingTest : public FileStorTestFixture {
    void setupDisks() {
        FileStorTestFixture::setupPersistenceThreads(1);
        _node->setPersistenceProvider(std::make_unique<spi::dummy::DummyPersistence>(_node->getTypeRepo()));
        _node->getPersistenceProvider().initialize();
    }

    void SetUp() override;
};

void
MergeBlockingTest::SetUp()
{
    setupDisks();
}

namespace {

api::StorageMessageAddress
makeAddress() {
    static vespalib::string _storage("storage");
    return api::StorageMessageAddress(&_storage, lib::NodeType::STORAGE, 0);
}

void
assignCommandMeta(api::StorageCommand& msg) {
    msg.setAddress(makeAddress());
    msg.setSourceIndex(0);
}

std::vector<api::MergeBucketCommand::Node>
getNodes() {
    return std::vector<api::MergeBucketCommand::Node>({0, 1});
}

std::vector<api::MergeBucketCommand::Node>
getNodesWithForwarding() {
    return std::vector<api::MergeBucketCommand::Node>({0, 1, 2});
}

std::shared_ptr<api::MergeBucketCommand>
createMerge(const document::BucketId& bucket) {
    auto cmd = std::make_shared<api::MergeBucketCommand>(
            makeDocumentBucket(bucket), getNodes(), api::Timestamp(1000));
    assignCommandMeta(*cmd);
    return cmd;
}

std::shared_ptr<api::GetBucketDiffCommand>
createGetDiff(const document::BucketId& bucket,
              const std::vector<api::MergeBucketCommand::Node>& nodes)
{
    auto cmd = std::make_shared<api::GetBucketDiffCommand>(
            makeDocumentBucket(bucket), nodes, api::Timestamp(1000));
    assignCommandMeta(*cmd);
    return cmd;
}

std::shared_ptr<api::ApplyBucketDiffCommand>
createApplyDiff(const document::BucketId& bucket,
                const std::vector<api::MergeBucketCommand::Node>& nodes) {
    auto cmd = std::make_shared<api::ApplyBucketDiffCommand>(makeDocumentBucket(bucket), nodes);
    assignCommandMeta(*cmd);
    return cmd;
}

const document::BucketId leafBucket(17, 1);
const document::BucketId innerBucket(16, 1);
const document::BucketId innerBucket2(15, 1);

}

TEST_F(MergeBlockingTest, reject_merge_for_inconsistent_inner_bucket) {
    TestFileStorComponents c(*this);
    createBucket(leafBucket);

    auto cmd = createMerge(innerBucket);
    c.top.sendDown(cmd);

    ASSERT_NO_FATAL_FAILURE(expectAbortedReply<api::MergeBucketReply>(c.top));
    EXPECT_FALSE(bucketExistsInDb(innerBucket));
}

TEST_F(MergeBlockingTest, reject_merge_for_inconsistent_leaf_bucket) {
    TestFileStorComponents c(*this);
    createBucket(innerBucket);

    auto cmd = createMerge(leafBucket);
    c.top.sendDown(cmd);

    ASSERT_NO_FATAL_FAILURE(expectAbortedReply<api::MergeBucketReply>(c.top));
    EXPECT_FALSE(bucketExistsInDb(leafBucket));
}

TEST_F(MergeBlockingTest, reject_get_diff_with_inconsistent_bucket) {
    TestFileStorComponents c(*this);
    ASSERT_TRUE(innerBucket.contains(leafBucket));
    createBucket(innerBucket);

    auto cmd = createGetDiff(leafBucket, getNodes());
    c.top.sendDown(cmd);

    ASSERT_NO_FATAL_FAILURE(expectAbortedReply<api::GetBucketDiffReply>(c.top));
    EXPECT_FALSE(bucketExistsInDb(leafBucket));
}

TEST_F(MergeBlockingTest, reject_apply_diff_when_bucket_has_become_inconsistent) {
    TestFileStorComponents c(*this);
    createBucket(leafBucket);
    createBucket(innerBucket);

    auto applyDiff = createApplyDiff(innerBucket, getNodes());
    c.top.sendDown(applyDiff);

    ASSERT_NO_FATAL_FAILURE(expectAbortedReply<api::ApplyBucketDiffReply>(c.top));
}

TEST_F(MergeBlockingTest, reject_apply_diff_reply_when_bucket_has_become_inconsistent) {
    TestFileStorComponents c(*this);
    createBucket(innerBucket);

    auto applyDiff = createApplyDiff(innerBucket, getNodesWithForwarding());
    c.top.sendDown(applyDiff);
    c.top.waitForMessages(1, MSG_WAIT_TIME);

    auto fwdDiff = c.top.getAndRemoveMessage(api::MessageType::APPLYBUCKETDIFF);
    auto& diffCmd = dynamic_cast<api::ApplyBucketDiffCommand&>(*fwdDiff);

    auto diffReply = std::make_shared<api::ApplyBucketDiffReply>(diffCmd);
    createBucket(leafBucket);
    c.top.sendDown(diffReply);

    ASSERT_NO_FATAL_FAILURE(expectAbortedReply<api::ApplyBucketDiffReply>(c.top));
}

TEST_F(MergeBlockingTest, reject_get_diff_reply_when_bucket_has_become_inconsistent) {
    TestFileStorComponents c(*this);
    createBucket(innerBucket);

    auto getDiff = createGetDiff(innerBucket, getNodesWithForwarding());
    c.top.sendDown(getDiff);
    c.top.waitForMessages(1, MSG_WAIT_TIME);

    auto fwdDiff = c.top.getAndRemoveMessage(api::MessageType::GETBUCKETDIFF);
    auto& diffCmd =  dynamic_cast<api::GetBucketDiffCommand&>(*fwdDiff);

    auto diffReply = std::make_shared<api::GetBucketDiffReply>(diffCmd);
    createBucket(innerBucket2);
    c.top.sendDown(diffReply);

    ASSERT_NO_FATAL_FAILURE(expectAbortedReply<api::GetBucketDiffReply>(c.top));
}

/**
 * Test case for buckets in ticket 6389558, comment #4.
 */
TEST_F(MergeBlockingTest, reject_merge_when_low_used_bit_count) {
    document::BucketId superBucket(1, 0x1);
    document::BucketId subBucket(2, 0x1);

    ASSERT_TRUE(superBucket.contains(subBucket));

    TestFileStorComponents c(*this);
    createBucket(superBucket);

    auto cmd = createMerge(subBucket);
    c.top.sendDown(cmd);

    ASSERT_NO_FATAL_FAILURE(expectAbortedReply<api::MergeBucketReply>(c.top));
    EXPECT_FALSE(bucketExistsInDb(subBucket));
}

} // ns storage
