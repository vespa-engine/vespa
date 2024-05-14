// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/testhelper.h>
#include <tests/common/storage_config_set.h>
#include <vespa/storage/storageserver/distributornode.h>
#include <vespa/storage/storageserver/servicelayernode.h>
#include <vespa/storageserver/app/distributorprocess.h>
#include <vespa/storageserver/app/dummyservicelayerprocess.h>
#include <vespa/messagebus/message.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>

LOG_SETUP(".storageservertest");

using namespace std::chrono_literals;

namespace storage {

struct StorageServerTest : public ::testing::Test {
    std::unique_ptr<mbus::Slobrok> slobrok;
    std::unique_ptr<StorageConfigSet> dist_config;
    std::unique_ptr<StorageConfigSet> stor_config;

    StorageServerTest();
    ~StorageServerTest() override;

    void SetUp() override;
    void TearDown() override;

};

StorageServerTest::StorageServerTest() = default;
StorageServerTest::~StorageServerTest() = default;

namespace {

struct Node {
    virtual ~Node() = default;
    virtual StorageNode& getNode() = 0;
    virtual StorageNodeContext& getContext() = 0;
};

struct Distributor final : public Node {
    DistributorProcess _process;

    explicit Distributor(const config::ConfigUri& config_uri);
    ~Distributor() override;

    StorageNode& getNode() override { return _process.getNode(); }
    StorageNodeContext& getContext() override { return _process.getContext(); }
};

struct Storage final : public Node {
    DummyServiceLayerProcess _process;
    StorageComponent::UP _component;

    explicit Storage(const config::ConfigUri& config_uri);
    ~Storage() override;

    StorageNode& getNode() override { return _process.getNode(); }
    StorageNodeContext& getContext() override { return _process.getContext(); }
};

Distributor::Distributor(const config::ConfigUri& config_uri)
    : _process(config_uri)
{
    _process.setupConfig(60000ms);
    _process.createNode();
}

Distributor::~Distributor() = default;

Storage::Storage(const config::ConfigUri& config_uri)
    : _process(config_uri)
{
    _process.setupConfig(60000ms);
    _process.createNode();
    _component = std::make_unique<StorageComponent>(getContext().getComponentRegister(), "test");
}

Storage::~Storage() = default;

}

void
StorageServerTest::SetUp()
{
    slobrok = std::make_unique<mbus::Slobrok>();
    dist_config = StorageConfigSet::make_distributor_node_config();
    stor_config = StorageConfigSet::make_storage_node_config();
    dist_config->set_slobrok_config_port(slobrok->port());
    stor_config->set_slobrok_config_port(slobrok->port());
}

void
StorageServerTest::TearDown()
{
    stor_config.reset();
    dist_config.reset();
    slobrok.reset();
}

TEST_F(StorageServerTest, distributor_server_can_be_instantiated)
{
    Distributor distServer(dist_config->config_uri());
}

TEST_F(StorageServerTest, storage_server_can_be_instantiated)
{
    Storage storServer(stor_config->config_uri());
}

}
