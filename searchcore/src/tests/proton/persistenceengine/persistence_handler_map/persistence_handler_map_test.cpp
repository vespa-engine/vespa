// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/persistenceengine/ipersistencehandler.h>
#include <vespa/searchcore/proton/persistenceengine/persistence_handler_map.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace document;
using namespace proton;

using HandlerSnapshot = PersistenceHandlerMap::HandlerSnapshot;

struct DummyPersistenceHandler : public IPersistenceHandler {
    using SP = std::shared_ptr<DummyPersistenceHandler>;
    void initialize() override {}
    void handlePut(FeedToken, const storage::spi::Bucket &, storage::spi::Timestamp, DocumentSP) override {}
    void handleUpdate(FeedToken, const storage::spi::Bucket &, storage::spi::Timestamp, DocumentUpdateSP) override {}
    void handleRemove(FeedToken, const storage::spi::Bucket &, storage::spi::Timestamp, const document::DocumentId &) override {}
    void handleRemoveByGid(FeedToken, const storage::spi::Bucket&, storage::spi::Timestamp, std::string_view, const GlobalId&) override { }
    void handleListBuckets(IBucketIdListResultHandler &) override {}
    void handleSetClusterState(const storage::spi::ClusterState &, IGenericResultHandler &) override {}
    void handleSetActiveState(const storage::spi::Bucket &, storage::spi::BucketInfo::ActiveState, std::shared_ptr<IGenericResultHandler>) override {}
    void handleGetBucketInfo(const storage::spi::Bucket &, IBucketInfoResultHandler &) override {}
    void handleCreateBucket(FeedToken, const storage::spi::Bucket &) override {}
    void handleDeleteBucket(FeedToken, const storage::spi::Bucket &) override {}
    void handleGetModifiedBuckets(IBucketIdListResultHandler &) override {}
    void handleSplit(FeedToken, const storage::spi::Bucket &, const storage::spi::Bucket &, const storage::spi::Bucket &) override {}
    void handleJoin(FeedToken, const storage::spi::Bucket &, const storage::spi::Bucket &, const storage::spi::Bucket &) override {}

    RetrieversSP getDocumentRetrievers(storage::spi::ReadConsistency) override { return RetrieversSP(); }
    void handleListActiveBuckets(IBucketIdListResultHandler &) override {}
    void handlePopulateActiveBuckets(document::BucketId::List, IGenericResultHandler &) override {}
    const DocTypeName & doc_type_name() const noexcept override { abort(); }
};

BucketSpace space_1(1);
BucketSpace space_2(2);
BucketSpace space_null(3);
DocTypeName type_a("a");
DocTypeName type_b("b");
DocTypeName type_c("c");
DummyPersistenceHandler::SP handler_a(std::make_shared<DummyPersistenceHandler>());
DummyPersistenceHandler::SP handler_b(std::make_shared<DummyPersistenceHandler>());
DummyPersistenceHandler::SP handler_c(std::make_shared<DummyPersistenceHandler>());
DummyPersistenceHandler::SP handler_a_new(std::make_shared<DummyPersistenceHandler>());

void
assertSnapshot(const std::vector<IPersistenceHandler::SP> &exp, HandlerSnapshot snapshot, const std::string& label)
{
    SCOPED_TRACE(label);
    EXPECT_EQ(exp.size(), snapshot.size());
    auto &sequence = snapshot.handlers();
    for (size_t i = 0; i < exp.size() && sequence.valid(); ++i, sequence.next()) {
        EXPECT_EQ(exp[i].get(), sequence.get());
    }
}

struct PersistenceHandlerMapTest : public ::testing::Test {
    PersistenceHandlerMap map;
    PersistenceHandlerMapTest();
    ~PersistenceHandlerMapTest() override;
};

PersistenceHandlerMapTest::PersistenceHandlerMapTest()
    : ::testing::Test(),
      map()
{
    EXPECT_TRUE(!map.putHandler(space_1, type_a, handler_a));
    EXPECT_TRUE(!map.putHandler(space_1, type_b, handler_b));
    EXPECT_TRUE(!map.putHandler(space_2, type_c, handler_c));
}

PersistenceHandlerMapTest::~PersistenceHandlerMapTest() = default;

TEST_F(PersistenceHandlerMapTest, require_that_handlers_can_be_retrieved)
{
    EXPECT_EQ(handler_a.get(), map.getHandler(space_1, type_a));
    EXPECT_EQ(handler_b.get(), map.getHandler(space_1, type_b));
    EXPECT_EQ(handler_c.get(), map.getHandler(space_2, type_c));
    EXPECT_EQ(nullptr, map.getHandler(space_1, type_c));
    EXPECT_EQ(nullptr, map.getHandler(space_null, type_a));
}

TEST_F(PersistenceHandlerMapTest, require_that_old_handler_is_returned_if_replaced_by_new_handler)
{
    EXPECT_EQ(handler_a.get(), map.putHandler(space_1, type_a, handler_a_new).get());
    EXPECT_EQ(handler_a_new.get(), map.getHandler(space_1, type_a));
}

TEST_F(PersistenceHandlerMapTest, require_that_handler_can_be_removed_and_old_handler_returned)
{
    EXPECT_EQ(handler_a.get(), map.removeHandler(space_1, type_a).get());
    EXPECT_EQ(nullptr, map.getHandler(space_1, type_a));
    EXPECT_TRUE(!map.removeHandler(space_1, type_c));
}

TEST_F(PersistenceHandlerMapTest, require_that_handler_snapshot_can_be_retrieved_for_all_handlers)
{
    assertSnapshot({handler_c, handler_a, handler_b}, map.getHandlerSnapshot(), "all spaces");
}

TEST_F(PersistenceHandlerMapTest, require_that_handler_snapshot_can_be_retrieved_for_given_bucket_space)
{
    assertSnapshot({handler_a, handler_b}, map.getHandlerSnapshot(space_1), "space_1");
    assertSnapshot({handler_c}, map.getHandlerSnapshot(space_2), "space_2");
    assertSnapshot({}, map.getHandlerSnapshot(space_null),"space_3");
}

GTEST_MAIN_RUN_ALL_TESTS()
