// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for feedstates.

#include <vespa/log/log.h>
LOG_SETUP("feedstates_test");

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/server/feedstates.h>
#include <vespa/searchcore/proton/server/ireplayconfig.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/test/dummy_feed_view.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>

using document::BucketId;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::TestDocRepo;
using search::transactionlog::Packet;
using search::SerialNum;
using storage::spi::Timestamp;
using vespalib::ConstBufferRef;
using vespalib::nbostream;
using namespace proton;

namespace {

struct MyFeedView : public test::DummyFeedView {
    TestDocRepo repo;
    DocumentTypeRepo::SP repo_sp;
    int remove_handled;

    MyFeedView();
    ~MyFeedView();

    const DocumentTypeRepo::SP &getDocumentTypeRepo() const override { return repo_sp; }
    void handleRemove(FeedToken , const RemoveOperation &) override { ++remove_handled; }
};

MyFeedView::MyFeedView() : repo_sp(repo.getTypeRepoSp()), remove_handled(0) {}
MyFeedView::~MyFeedView() {}

struct MyReplayConfig : IReplayConfig {
    virtual void replayConfig(SerialNum) override {}
};

struct InstantExecutor : vespalib::Executor {
    virtual Task::UP execute(Task::UP task) override {
        task->run();
        return Task::UP();
    }
};

struct Fixture
{
    MyFeedView feed_view1;
    MyFeedView feed_view2;
    IFeedView *feed_view_ptr;
    MyReplayConfig replay_config;
    MemoryConfigStore config_store;
    BucketDBOwner _bucketDB;
    bucketdb::BucketDBHandler _bucketDBHandler;
    ReplayTransactionLogState state;

    Fixture();
    ~Fixture();
};

Fixture::Fixture()
    : feed_view1(),
      feed_view2(),
      feed_view_ptr(&feed_view1),
      replay_config(),
      config_store(),
      _bucketDB(),
      _bucketDBHandler(_bucketDB),
      state("doctypename", feed_view_ptr, _bucketDBHandler, replay_config, config_store)
{
}
Fixture::~Fixture() {}


struct RemoveOperationContext
{
    DocumentId doc_id;
    RemoveOperation op;
    nbostream str;
    std::unique_ptr<Packet> packet;

    RemoveOperationContext(search::SerialNum serial);
    ~RemoveOperationContext();
};

RemoveOperationContext::RemoveOperationContext(search::SerialNum serial)
    : doc_id("doc:foo:bar"),
      op(BucketFactory::getBucketId(doc_id), Timestamp(10), doc_id),
      str(), packet()
{
    op.serialize(str);
    ConstBufferRef buf(str.c_str(), str.wp());
    packet.reset(new Packet());
    packet->add(Packet::Entry(serial, FeedOperation::REMOVE, buf));
}
RemoveOperationContext::~RemoveOperationContext() {}
TEST_F("require that active FeedView can change during replay", Fixture)
{
    RemoveOperationContext opCtx(10);
    PacketWrapper::SP wrap(new PacketWrapper(*opCtx.packet, NULL));
    InstantExecutor executor;

    EXPECT_EQUAL(0, f.feed_view1.remove_handled);
    EXPECT_EQUAL(0, f.feed_view2.remove_handled);
    f.state.receive(wrap, executor);
    EXPECT_EQUAL(1, f.feed_view1.remove_handled);
    EXPECT_EQUAL(0, f.feed_view2.remove_handled);
    f.feed_view_ptr = &f.feed_view2;
    f.state.receive(wrap, executor);
    EXPECT_EQUAL(1, f.feed_view1.remove_handled);
    EXPECT_EQUAL(1, f.feed_view2.remove_handled);
}

TEST_F("require that replay progress is tracked", Fixture)
{
    RemoveOperationContext opCtx(10);
    TlsReplayProgress progress("test", 5, 15);
    PacketWrapper::SP wrap(new PacketWrapper(*opCtx.packet, &progress));
    InstantExecutor executor;

    f.state.receive(wrap, executor);
    EXPECT_EQUAL(10u, progress.getCurrent());
    EXPECT_EQUAL(0.5, progress.getProgress());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
