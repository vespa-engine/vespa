// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/testhelper.h>
#include <vespa/storage/storageserver/distributornode.h>
#include <vespa/storage/storageserver/servicelayernode.h>
#include <vespa/storageserver/app/distributorprocess.h>
#include <vespa/storageserver/app/dummyservicelayerprocess.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>

LOG_SETUP(".storageservertest");

using namespace std::chrono_literals;

namespace storage {

struct StorageServerTest : public ::testing::Test {
    std::unique_ptr<mbus::Slobrok> slobrok;
    std::unique_ptr<vdstestlib::DirConfig> distConfig;
    std::unique_ptr<vdstestlib::DirConfig> storConfig;

    StorageServerTest();
    ~StorageServerTest();

    void SetUp() override;
    void TearDown() override;

};

StorageServerTest::StorageServerTest() = default;
StorageServerTest::~StorageServerTest() = default;

namespace {

struct Node {
    virtual ~Node() {}
    virtual StorageNode& getNode() = 0;
    virtual StorageNodeContext& getContext() = 0;
};

struct Distributor : public Node {
    DistributorProcess _process;

    Distributor(vdstestlib::DirConfig& config);
    ~Distributor();

    virtual StorageNode& getNode() override { return _process.getNode(); }
    virtual StorageNodeContext& getContext() override { return _process.getContext(); }
};

struct Storage : public Node {
    DummyServiceLayerProcess _process;
    StorageComponent::UP _component;

    Storage(vdstestlib::DirConfig& config);
    ~Storage();

    virtual StorageNode& getNode() override { return _process.getNode(); }
    virtual StorageNodeContext& getContext() override { return _process.getContext(); }
};

Distributor::Distributor(vdstestlib::DirConfig& config)
    : _process(config.getConfigId())
{
    _process.setupConfig(60000ms);
    _process.createNode();
}

Distributor::~Distributor() = default;

Storage::Storage(vdstestlib::DirConfig& config)
    : _process(config.getConfigId())
{
    _process.setupConfig(60000ms);
    _process.createNode();
    _component.reset(new StorageComponent(
    getContext().getComponentRegister(), "test"));
}

Storage::~Storage() = default;

}

void
StorageServerTest::SetUp()
{
    [[maybe_unused]] int systemResult = system("chmod -R 755 vdsroot");
    systemResult = system("rm -rf vdsroot*");
    slobrok.reset(new mbus::Slobrok);
    distConfig.reset(new vdstestlib::DirConfig(getStandardConfig(false)));
    storConfig.reset(new vdstestlib::DirConfig(getStandardConfig(true)));
    addSlobrokConfig(*distConfig, *slobrok);
    addSlobrokConfig(*storConfig, *slobrok);
    storConfig->getConfig("stor-filestor").set("fail_disk_after_error_count", "1");
    systemResult = system("mkdir -p vdsroot/disks/d0");
    systemResult = system("mkdir -p vdsroot.distributor");
}

void
StorageServerTest::TearDown()
{
    storConfig.reset(nullptr);
    distConfig.reset(nullptr);
    slobrok.reset(nullptr);
}

TEST_F(StorageServerTest, distributor_server_can_be_instantiated)
{
    Distributor distServer(*distConfig);
}

TEST_F(StorageServerTest, storage_server_can_be_instantiated)
{
    Storage storServer(*storConfig);
}

}
