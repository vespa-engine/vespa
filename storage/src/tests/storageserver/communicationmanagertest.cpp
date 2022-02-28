// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageserver/communicationmanager.h>

#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/documentapi/messagebus/messages/getdocumentmessage.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/getdocumentreply.h>
#include <thread>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

vespalib::string _Storage("storage");

struct CommunicationManagerTest : Test {

    static constexpr uint32_t MESSAGE_WAIT_TIME_SEC = 60;

    void doTestConfigPropagation(bool isContentNode);

    std::shared_ptr<api::StorageCommand> createDummyCommand(
            api::StorageMessage::Priority priority)
    {
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId(0)),
                                                     document::DocumentId("id:ns:mytype::mydoc"),
                                                     document::AllFields::NAME);
        cmd->setAddress(api::StorageMessageAddress::create(&_Storage, lib::NodeType::STORAGE, 1));
        cmd->setPriority(priority);
        return cmd;
    }
};

namespace {

void
wait_for_slobrok_visibility(const CommunicationManager& mgr,
                            const api::StorageMessageAddress& addr)
{
    const auto deadline = vespalib::steady_clock::now() + 60s;
    do {
        if (mgr.address_visible_in_slobrok(addr)) {
            return;
        }
        std::this_thread::sleep_for(10ms);
    } while (vespalib::steady_clock::now() < deadline);
    FAIL() << "Timed out waiting for address " << addr.toString() << " to be visible in Slobrok";
}

}

TEST_F(CommunicationManagerTest, simple) {
    mbus::Slobrok slobrok;
    vdstestlib::DirConfig distConfig(getStandardConfig(false));
    vdstestlib::DirConfig storConfig(getStandardConfig(true));
    distConfig.getConfig("stor-server").set("node_index", "1");
    storConfig.getConfig("stor-server").set("node_index", "1");
    addSlobrokConfig(distConfig, slobrok);
    addSlobrokConfig(storConfig, slobrok);

    // Set up a "distributor" and a "storage" node with communication
    // managers and a dummy storage link below we can use for testing.
    TestServiceLayerApp storNode(storConfig.getConfigId());
    TestDistributorApp distNode(distConfig.getConfigId());

    CommunicationManager distributor(distNode.getComponentRegister(),
                                     config::ConfigUri(distConfig.getConfigId()));
    CommunicationManager storage(storNode.getComponentRegister(),
                                 config::ConfigUri(storConfig.getConfigId()));
    DummyStorageLink *distributorLink = new DummyStorageLink();
    DummyStorageLink *storageLink = new DummyStorageLink();
    distributor.push_back(std::unique_ptr<StorageLink>(distributorLink));
    storage.push_back(std::unique_ptr<StorageLink>(storageLink));
    distributor.open();
    storage.open();

    auto stor_addr  = api::StorageMessageAddress::create(&_Storage, lib::NodeType::STORAGE, 1);
    auto distr_addr = api::StorageMessageAddress::create(&_Storage, lib::NodeType::DISTRIBUTOR, 1);
    // It is undefined when the logical nodes will be visible in each others Slobrok
    // mirrors, so explicitly wait until mutual visibility is ensured. Failure to do this
    // might cause the below message to be immediately bounced due to failing to map the
    // storage address to an actual RPC endpoint.
    ASSERT_NO_FATAL_FAILURE(wait_for_slobrok_visibility(distributor, stor_addr));
    ASSERT_NO_FATAL_FAILURE(wait_for_slobrok_visibility(storage, distr_addr));

    // Send a message through from distributor to storage
    auto cmd = std::make_shared<api::GetCommand>(
            makeDocumentBucket(document::BucketId(0)), document::DocumentId("id:ns:mytype::mydoc"), document::AllFields::NAME);
    cmd->setAddress(stor_addr);
    distributorLink->sendUp(cmd);
    storageLink->waitForMessages(1, MESSAGE_WAIT_TIME_SEC);
    ASSERT_GT(storageLink->getNumCommands(), 0);
    auto cmd2 = std::dynamic_pointer_cast<api::StorageCommand>(storageLink->getCommand(0));
    EXPECT_EQ("id:ns:mytype::mydoc", dynamic_cast<api::GetCommand&>(*cmd2).getDocumentId().toString());
    // Reply to the message
    std::shared_ptr<api::StorageReply> reply(cmd2->makeReply().release());
    storageLink->sendUp(reply);
    storageLink->sendUp(reply);
    distributorLink->waitForMessages(1, MESSAGE_WAIT_TIME_SEC);
    ASSERT_GT(distributorLink->getNumCommands(), 0);
    auto reply2 = std::dynamic_pointer_cast<api::GetReply>(distributorLink->getCommand(0));
    EXPECT_FALSE(reply2->wasFound());
}

void
CommunicationManagerTest::doTestConfigPropagation(bool isContentNode)
{
    mbus::Slobrok slobrok;
    vdstestlib::DirConfig config(getStandardConfig(isContentNode));
    config.getConfig("stor-server").set("node_index", "1");
    auto& cfg = config.getConfig("stor-communicationmanager");
    cfg.set("mbus_content_node_max_pending_count", "12345");
    cfg.set("mbus_content_node_max_pending_size", "555666");
    cfg.set("mbus_distributor_node_max_pending_count", "6789");
    cfg.set("mbus_distributor_node_max_pending_size", "777888");
    addSlobrokConfig(config, slobrok);

    std::unique_ptr<TestStorageApp> node;
    if (isContentNode) {
        node = std::make_unique<TestServiceLayerApp>(config.getConfigId());
    } else {
        node = std::make_unique<TestDistributorApp>(config.getConfigId());
    }

    CommunicationManager commMgr(node->getComponentRegister(),
                                 config::ConfigUri(config.getConfigId()));
    DummyStorageLink *storageLink = new DummyStorageLink();
    commMgr.push_back(std::unique_ptr<StorageLink>(storageLink));
    commMgr.open();

    // Outer type is RPCMessageBus, which wraps regular MessageBus.
    auto& mbus = commMgr.getMessageBus().getMessageBus();
    if (isContentNode) {
        EXPECT_EQ(12345, mbus.getMaxPendingCount());
        EXPECT_EQ(555666, mbus.getMaxPendingSize());
    } else {
        EXPECT_EQ(6789, mbus.getMaxPendingCount());
        EXPECT_EQ(777888, mbus.getMaxPendingSize());
    }

    // Test live reconfig of limits.
    using ConfigBuilder
        = vespa::config::content::core::StorCommunicationmanagerConfigBuilder;
    auto liveCfg = std::make_unique<ConfigBuilder>();
    liveCfg->mbusContentNodeMaxPendingCount = 777777;
    liveCfg->mbusDistributorNodeMaxPendingCount = 999999;

    commMgr.configure(std::move(liveCfg));
    if (isContentNode) {
        EXPECT_EQ(777777, mbus.getMaxPendingCount());
    } else {
        EXPECT_EQ(999999, mbus.getMaxPendingCount());
    }
}

TEST_F(CommunicationManagerTest, dist_pending_limit_configs_are_propagated_to_message_bus) {
    doTestConfigPropagation(false);
}

TEST_F(CommunicationManagerTest, stor_pending_limit_configs_are_propagated_to_message_bus) {
    doTestConfigPropagation(true);
}

TEST_F(CommunicationManagerTest, commands_are_dequeued_in_fifo_order) {
    mbus::Slobrok slobrok;
    vdstestlib::DirConfig storConfig(getStandardConfig(true));
    storConfig.getConfig("stor-server").set("node_index", "1");
    addSlobrokConfig(storConfig, slobrok);
    TestServiceLayerApp storNode(storConfig.getConfigId());

    CommunicationManager storage(storNode.getComponentRegister(),
                                 config::ConfigUri(storConfig.getConfigId()));
    DummyStorageLink *storageLink = new DummyStorageLink();
    storage.push_back(std::unique_ptr<StorageLink>(storageLink));
    storage.open();

    // Message dequeing does not start before we invoke `open` on the storage
    // link chain, so we enqueue messages in randomized priority order before
    // doing so. After starting the thread, we should get messages down
    // the chain in a deterministic FIFO order and _not_ priority-order.
    // Lower number == higher priority.
    std::vector<api::StorageMessage::Priority> pris{200, 0, 255, 128};
    for (auto pri : pris) {
        storage.dispatch_async(createDummyCommand(pri));
    }
    storageLink->waitForMessages(pris.size(), MESSAGE_WAIT_TIME_SEC);

    for (size_t i = 0; i < pris.size(); ++i) {
        // Casting is just to avoid getting mismatched values printed to the
        // output verbatim as chars.
        EXPECT_EQ(
                uint32_t(pris[i]),
                uint32_t(storageLink->getCommand(i)->getPriority()));
    }
}

TEST_F(CommunicationManagerTest, replies_are_dequeued_in_fifo_order) {
    mbus::Slobrok slobrok;
    vdstestlib::DirConfig storConfig(getStandardConfig(true));
    storConfig.getConfig("stor-server").set("node_index", "1");
    addSlobrokConfig(storConfig, slobrok);
    TestServiceLayerApp storNode(storConfig.getConfigId());

    CommunicationManager storage(storNode.getComponentRegister(),
                                 config::ConfigUri(storConfig.getConfigId()));
    DummyStorageLink *storageLink = new DummyStorageLink();
    storage.push_back(std::unique_ptr<StorageLink>(storageLink));
    storage.open();

    std::vector<api::StorageMessage::Priority> pris{200, 0, 255, 128};
    for (auto pri : pris) {
        storage.dispatch_async(createDummyCommand(pri)->makeReply());
    }
    storageLink->waitForMessages(pris.size(), MESSAGE_WAIT_TIME_SEC);

    // Want FIFO order for replies, not priority-sorted order.
    for (size_t i = 0; i < pris.size(); ++i) {
        EXPECT_EQ(
                uint32_t(pris[i]),
                uint32_t(storageLink->getCommand(i)->getPriority()));
    }
}

struct MockMbusReplyHandler : mbus::IReplyHandler {
    std::vector<std::unique_ptr<mbus::Reply>> replies;

    void handleReply(std::unique_ptr<mbus::Reply> msg) override {
        replies.emplace_back(std::move(msg));
    }
};

struct CommunicationManagerFixture {
    MockMbusReplyHandler reply_handler;
    mbus::Slobrok slobrok;
    std::unique_ptr<TestServiceLayerApp> node;
    std::unique_ptr<CommunicationManager> comm_mgr;
    DummyStorageLink* bottom_link;

    CommunicationManagerFixture() {
        vdstestlib::DirConfig stor_config(getStandardConfig(true));
        stor_config.getConfig("stor-server").set("node_index", "1");
        addSlobrokConfig(stor_config, slobrok);

        node = std::make_unique<TestServiceLayerApp>(stor_config.getConfigId());
        comm_mgr = std::make_unique<CommunicationManager>(node->getComponentRegister(),
                                                          config::ConfigUri(stor_config.getConfigId()));
        bottom_link = new DummyStorageLink();
        comm_mgr->push_back(std::unique_ptr<StorageLink>(bottom_link));
        comm_mgr->open();
    }
    ~CommunicationManagerFixture();

    template <typename T>
    std::unique_ptr<T> documentapi_message_for_space(const char *space) {
        auto cmd = std::make_unique<T>(document::DocumentId(vespalib::make_string("id::%s::stuff", space)));
        // Bind reply handling to our own mock handler
        cmd->pushHandler(reply_handler);
        return cmd;
    }

    std::unique_ptr<documentapi::RemoveDocumentMessage> documentapi_remove_message_for_space(const char *space) {
        return documentapi_message_for_space<documentapi::RemoveDocumentMessage>(space);
    }

    std::unique_ptr<documentapi::GetDocumentMessage> documentapi_get_message_for_space(const char *space) {
        return documentapi_message_for_space<documentapi::GetDocumentMessage>(space);
    }
};

CommunicationManagerFixture::~CommunicationManagerFixture() = default;

using vespa::config::content::core::BucketspacesConfigBuilder;

namespace {

BucketspacesConfigBuilder::Documenttype doc_type(vespalib::stringref name, vespalib::stringref space) {
    BucketspacesConfigBuilder::Documenttype dt;
    dt.name = name;
    dt.bucketspace = space;
    return dt;
}

}

TEST_F(CommunicationManagerTest, bucket_space_config_can_be_updated_live) {
    CommunicationManagerFixture f;
    BucketspacesConfigBuilder config;
    config.documenttype.emplace_back(doc_type("foo", "default"));
    config.documenttype.emplace_back(doc_type("bar", "global"));
    f.comm_mgr->updateBucketSpacesConfig(config);

    f.comm_mgr->handleMessage(f.documentapi_remove_message_for_space("bar"));
    f.comm_mgr->handleMessage(f.documentapi_remove_message_for_space("foo"));
    f.bottom_link->waitForMessages(2, MESSAGE_WAIT_TIME_SEC);

    auto cmd1 = f.bottom_link->getCommand(0);
    EXPECT_EQ(document::FixedBucketSpaces::global_space(), cmd1->getBucket().getBucketSpace());

    auto cmd2 = f.bottom_link->getCommand(1);
    EXPECT_EQ(document::FixedBucketSpaces::default_space(), cmd2->getBucket().getBucketSpace());

    config.documenttype[1] = doc_type("bar", "default");
    f.comm_mgr->updateBucketSpacesConfig(config);
    f.comm_mgr->handleMessage(f.documentapi_remove_message_for_space("bar"));
    f.bottom_link->waitForMessages(3, MESSAGE_WAIT_TIME_SEC);

    auto cmd3 = f.bottom_link->getCommand(2);
    EXPECT_EQ(document::FixedBucketSpaces::default_space(), cmd3->getBucket().getBucketSpace());

    EXPECT_EQ(uint64_t(0), f.comm_mgr->metrics().bucketSpaceMappingFailures.getValue());
}

TEST_F(CommunicationManagerTest, unmapped_bucket_space_documentapi_request_returns_error_reply) {
    CommunicationManagerFixture f;

    BucketspacesConfigBuilder config;
    config.documenttype.emplace_back(doc_type("foo", "default"));
    f.comm_mgr->updateBucketSpacesConfig(config);

    EXPECT_EQ(uint64_t(0), f.comm_mgr->metrics().bucketSpaceMappingFailures.getValue());

    f.comm_mgr->handleMessage(f.documentapi_remove_message_for_space("fluff"));
    ASSERT_EQ(1, f.reply_handler.replies.size());
    auto& reply = *f.reply_handler.replies[0];
    ASSERT_TRUE(reply.hasErrors());
    EXPECT_EQ(static_cast<uint32_t>(api::ReturnCode::REJECTED), reply.getError(0).getCode());

    EXPECT_EQ(uint64_t(1), f.comm_mgr->metrics().bucketSpaceMappingFailures.getValue());
}

TEST_F(CommunicationManagerTest, unmapped_bucket_space_for_get_documentapi_request_returns_error_reply) {
    CommunicationManagerFixture f;

    BucketspacesConfigBuilder config;
    config.documenttype.emplace_back(doc_type("foo", "default"));
    f.comm_mgr->updateBucketSpacesConfig(config);

    f.comm_mgr->handleMessage(f.documentapi_get_message_for_space("fluff"));
    ASSERT_EQ(1, f.reply_handler.replies.size());
    auto& reply = *f.reply_handler.replies[0];
    ASSERT_TRUE(reply.hasErrors());
    EXPECT_EQ(static_cast<uint32_t>(api::ReturnCode::REJECTED), reply.getError(0).getCode());
    EXPECT_EQ(uint64_t(1), f.comm_mgr->metrics().bucketSpaceMappingFailures.getValue());
}

TEST_F(CommunicationManagerTest, communication_manager_swallows_internal_replies) {
    CommunicationManagerFixture f;
    auto msg = std::make_unique<RecheckBucketInfoCommand>(makeDocumentBucket({16, 1}));
    auto reply = std::shared_ptr<api::StorageReply>(msg->makeReply());
    EXPECT_TRUE(f.comm_mgr->onUp(reply)); // true == handled by storage link
}

} // storage
