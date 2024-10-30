// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for feedstates.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/server/feedstates.h>
#include <vespa/searchcore/proton/server/ireplayconfig.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/server/replay_throttling_policy.h>
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/test/dummy_feed_view.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::BucketId;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::TestDocRepo;
using search::transactionlog::Packet;
using search::SerialNum;
using storage::spi::Timestamp;
using vespalib::ConstBufferRef;
using vespalib::nbostream;
using vespalib::ForegroundThreadExecutor;
using namespace proton;

namespace {

struct MyFeedView : public test::DummyFeedView {
    TestDocRepo repo;
    std::shared_ptr<const DocumentTypeRepo> repo_sp;
    int remove_handled;

    MyFeedView();
    ~MyFeedView() override;

    const std::shared_ptr<const DocumentTypeRepo> &getDocumentTypeRepo() const override { return repo_sp; }
    void handleRemove(FeedToken , const RemoveOperation &) override { ++remove_handled; }
};

MyFeedView::MyFeedView() : repo_sp(repo.getTypeRepoSp()), remove_handled(0) {}
MyFeedView::~MyFeedView() = default;

struct MyReplayConfig : IReplayConfig {
    void replayConfig(SerialNum) override {}
};

struct MyIncSerialNum : IIncSerialNum {
    SerialNum _serial_num;
    explicit MyIncSerialNum(SerialNum serial_num)
        : _serial_num(serial_num)
    {
    }
    SerialNum inc_serial_num() override { return ++_serial_num; }
};

struct RemoveOperationContext
{
    DocumentId doc_id;
    RemoveOperationWithDocId op;
    nbostream str;
    std::unique_ptr<Packet> packet;

    explicit RemoveOperationContext(search::SerialNum serial);
    ~RemoveOperationContext();
};

RemoveOperationContext::RemoveOperationContext(search::SerialNum serial)
    : doc_id("id:ns:doctypename::bar"),
      op(BucketFactory::getBucketId(doc_id), Timestamp(10), doc_id),
      str(), packet(std::make_unique<Packet>(0xf000))
{
    op.serialize(str);
    ConstBufferRef buf(str.data(), str.wp());
    packet->add(Packet::Entry(serial, FeedOperation::REMOVE, buf));
}
RemoveOperationContext::~RemoveOperationContext() = default;

}

class FeedStatesTest : public ::testing::Test
{
protected:
    MyFeedView feed_view1;
    MyFeedView feed_view2;
    IFeedView *feed_view_ptr;
    MyReplayConfig replay_config;
    MemoryConfigStore config_store;
    bucketdb::BucketDBOwner _bucketDB;
    bucketdb::BucketDBHandler _bucketDBHandler;
    ReplayThrottlingPolicy _replay_throttling_policy;
    MyIncSerialNum _inc_serial_num;
    ReplayTransactionLogState state;

    FeedStatesTest();
    ~FeedStatesTest() override;
};

FeedStatesTest::FeedStatesTest()
    : ::testing::Test(),
      feed_view1(),
      feed_view2(),
      feed_view_ptr(&feed_view1),
      replay_config(),
      config_store(),
      _bucketDB(),
      _bucketDBHandler(_bucketDB),
      _replay_throttling_policy({}),
      _inc_serial_num(9u),
      state("doctypename", feed_view_ptr, _bucketDBHandler, replay_config, config_store, _replay_throttling_policy, _inc_serial_num)
{
}

FeedStatesTest::~FeedStatesTest() = default;

TEST_F(FeedStatesTest, require_that_active_FeedView_can_change_during_replay)
{
    ForegroundThreadExecutor executor;

    EXPECT_EQ(0, feed_view1.remove_handled);
    EXPECT_EQ(0, feed_view2.remove_handled);
    {
        RemoveOperationContext opCtx(10);
        auto wrap = std::make_shared<PacketWrapper>(*opCtx.packet, nullptr);
        state.receive(wrap, executor);
    }
    EXPECT_EQ(1, feed_view1.remove_handled);
    EXPECT_EQ(0, feed_view2.remove_handled);
    feed_view_ptr = &feed_view2;
    {
        RemoveOperationContext opCtx(11);
        auto wrap = std::make_shared<PacketWrapper>(*opCtx.packet, nullptr);
        state.receive(wrap, executor);
    }
    EXPECT_EQ(1, feed_view1.remove_handled);
    EXPECT_EQ(1, feed_view2.remove_handled);
}

TEST_F(FeedStatesTest, require_that_replay_progress_is_tracked)
{
    RemoveOperationContext opCtx(10);
    TlsReplayProgress progress("test", 5, 15);
    auto wrap = std::make_shared<PacketWrapper>(*opCtx.packet, &progress);
    ForegroundThreadExecutor executor;

    state.receive(wrap, executor);
    EXPECT_EQ(10u, progress.getCurrent());
    EXPECT_EQ(0.5, progress.getProgress());
}
