// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageserver/communicationmanager.h>

#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/memory/nomemorymanager.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {

struct CommunicationManagerTest : public CppUnit::TestFixture {
    void testSimple();
    void testDistPendingLimitConfigsArePropagatedToMessageBus();
    void testStorPendingLimitConfigsArePropagatedToMessageBus();
    void testCommandsAreDequeuedInPriorityOrder();
    void testRepliesAreDequeuedInFifoOrder();

    static constexpr uint32_t MESSAGE_WAIT_TIME_SEC = 60;

    void doTestConfigPropagation(bool isContentNode);

    std::shared_ptr<api::StorageCommand> createDummyCommand(
            api::StorageMessage::Priority priority)
    {
        auto cmd = std::make_shared<api::GetCommand>(
                document::BucketId(0),
                document::DocumentId("doc::mydoc"),
                "[all]");
        cmd->setAddress(api::StorageMessageAddress(
                    "storage", lib::NodeType::STORAGE, 1));
        cmd->setPriority(priority);
        return cmd;
    }

    CPPUNIT_TEST_SUITE(CommunicationManagerTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testDistPendingLimitConfigsArePropagatedToMessageBus);
    CPPUNIT_TEST(testStorPendingLimitConfigsArePropagatedToMessageBus);
    CPPUNIT_TEST(testCommandsAreDequeuedInPriorityOrder);
    CPPUNIT_TEST(testRepliesAreDequeuedInFifoOrder);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(CommunicationManagerTest);

void CommunicationManagerTest::testSimple()
{
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
                                     distConfig.getConfigId());
    CommunicationManager storage(storNode.getComponentRegister(),
                                 storConfig.getConfigId());
    DummyStorageLink *distributorLink = new DummyStorageLink();
    DummyStorageLink *storageLink = new DummyStorageLink();
    distributor.push_back(std::unique_ptr<StorageLink>(distributorLink));
    storage.push_back(std::unique_ptr<StorageLink>(storageLink));
    distributor.open();
    storage.open();

    FastOS_Thread::Sleep(1000);

    // Send a message through from distributor to storage
    std::shared_ptr<api::StorageCommand> cmd(
            new api::GetCommand(
                document::BucketId(0), document::DocumentId("doc::mydoc"), "[all]"));
    cmd->setAddress(api::StorageMessageAddress(
                "storage", lib::NodeType::STORAGE, 1));
    distributorLink->sendUp(cmd);
    storageLink->waitForMessages(1, MESSAGE_WAIT_TIME_SEC);
    CPPUNIT_ASSERT(storageLink->getNumCommands() > 0);
    std::shared_ptr<api::StorageCommand> cmd2(
            std::dynamic_pointer_cast<api::StorageCommand>(
                storageLink->getCommand(0)));
    CPPUNIT_ASSERT_EQUAL(
            vespalib::string("doc::mydoc"),
            static_cast<api::GetCommand&>(*cmd2).getDocumentId().toString());
        // Reply to the message
    std::shared_ptr<api::StorageReply> reply(cmd2->makeReply().release());
    storageLink->sendUp(reply);
    storageLink->sendUp(reply);
    distributorLink->waitForMessages(1, MESSAGE_WAIT_TIME_SEC);
    CPPUNIT_ASSERT(distributorLink->getNumCommands() > 0);
    std::shared_ptr<api::GetReply> reply2(
            std::dynamic_pointer_cast<api::GetReply>(
                distributorLink->getCommand(0)));
    CPPUNIT_ASSERT_EQUAL(false, reply2->wasFound());
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
                                 config.getConfigId());
    DummyStorageLink *storageLink = new DummyStorageLink();
    commMgr.push_back(std::unique_ptr<StorageLink>(storageLink));
    commMgr.open();

    // Outer type is RPCMessageBus, which wraps regular MessageBus.
    auto& mbus = commMgr.getMessageBus().getMessageBus();
    if (isContentNode) {
        CPPUNIT_ASSERT_EQUAL(uint32_t(12345), mbus.getMaxPendingCount());
        CPPUNIT_ASSERT_EQUAL(uint32_t(555666), mbus.getMaxPendingSize());
    } else {
        CPPUNIT_ASSERT_EQUAL(uint32_t(6789), mbus.getMaxPendingCount());
        CPPUNIT_ASSERT_EQUAL(uint32_t(777888), mbus.getMaxPendingSize());
    }

    // Test live reconfig of limits.
    using ConfigBuilder
        = vespa::config::content::core::StorCommunicationmanagerConfigBuilder;
    auto liveCfg = std::make_unique<ConfigBuilder>();
    liveCfg->mbusContentNodeMaxPendingCount = 777777;
    liveCfg->mbusDistributorNodeMaxPendingCount = 999999;

    commMgr.configure(std::move(liveCfg));
    if (isContentNode) {
        CPPUNIT_ASSERT_EQUAL(uint32_t(777777), mbus.getMaxPendingCount());
    } else {
        CPPUNIT_ASSERT_EQUAL(uint32_t(999999), mbus.getMaxPendingCount());
    }
}

void
CommunicationManagerTest::testDistPendingLimitConfigsArePropagatedToMessageBus()
{
    doTestConfigPropagation(false);
}

void
CommunicationManagerTest::testStorPendingLimitConfigsArePropagatedToMessageBus()
{
    doTestConfigPropagation(true);
}

void
CommunicationManagerTest::testCommandsAreDequeuedInPriorityOrder()
{
    mbus::Slobrok slobrok;
    vdstestlib::DirConfig storConfig(getStandardConfig(true));
    storConfig.getConfig("stor-server").set("node_index", "1");
    addSlobrokConfig(storConfig, slobrok);
    TestServiceLayerApp storNode(storConfig.getConfigId());

    CommunicationManager storage(storNode.getComponentRegister(),
                                 storConfig.getConfigId());
    DummyStorageLink *storageLink = new DummyStorageLink();
    storage.push_back(std::unique_ptr<StorageLink>(storageLink));

    // Message dequeing does not start before we invoke `open` on the storage
    // link chain, so we enqueue messages in randomized priority order before
    // doing so. After starting the thread, we should then get messages down
    // the chain in a deterministic, prioritized order.
    // Lower number == higher priority.
    std::vector<api::StorageMessage::Priority> pris{200, 0, 255, 128};
    for (auto pri : pris) {
        storage.enqueue(createDummyCommand(pri));
    }
    storage.open();
    storageLink->waitForMessages(pris.size(), MESSAGE_WAIT_TIME_SEC);

    std::sort(pris.begin(), pris.end());
    for (size_t i = 0; i < pris.size(); ++i) {
        // Casting is just to avoid getting mismatched values printed to the
        // output verbatim as chars.
        CPPUNIT_ASSERT_EQUAL(
                uint32_t(pris[i]),
                uint32_t(storageLink->getCommand(i)->getPriority()));
    }
}

void
CommunicationManagerTest::testRepliesAreDequeuedInFifoOrder()
{
    mbus::Slobrok slobrok;
    vdstestlib::DirConfig storConfig(getStandardConfig(true));
    storConfig.getConfig("stor-server").set("node_index", "1");
    addSlobrokConfig(storConfig, slobrok);
    TestServiceLayerApp storNode(storConfig.getConfigId());

    CommunicationManager storage(storNode.getComponentRegister(),
                                 storConfig.getConfigId());
    DummyStorageLink *storageLink = new DummyStorageLink();
    storage.push_back(std::unique_ptr<StorageLink>(storageLink));

    std::vector<api::StorageMessage::Priority> pris{200, 0, 255, 128};
    for (auto pri : pris) {
        storage.enqueue(createDummyCommand(pri)->makeReply());
    }
    storage.open();
    storageLink->waitForMessages(pris.size(), MESSAGE_WAIT_TIME_SEC);

    // Want FIFO order for replies, not priority-sorted order.
    for (size_t i = 0; i < pris.size(); ++i) {
        CPPUNIT_ASSERT_EQUAL(
                uint32_t(pris[i]),
                uint32_t(storageLink->getCommand(i)->getPriority()));
    }
}

} // storage
