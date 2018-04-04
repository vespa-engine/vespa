// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/messages.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/persistence/spi/test.h>
#include <sstream>

using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;

namespace storage {

spi::LoadType FileStorTestFixture::defaultLoadType = spi::LoadType(0, "default");
const uint32_t FileStorTestFixture::MSG_WAIT_TIME;

void
FileStorTestFixture::setupDisks(uint32_t diskCount)
{
    std::string rootOfRoot = "todo-make-unique-filestorefixture";
    _config.reset(new vdstestlib::DirConfig(getStandardConfig(true, rootOfRoot)));

    _config2.reset(new vdstestlib::DirConfig(*_config));
    _config2->getConfig("stor-server").set("root_folder", (rootOfRoot + "-vdsroot.2"));
    _config2->getConfig("stor-devices").set("root_folder", (rootOfRoot + "-vdsroot.2"));
    _config2->getConfig("stor-server").set("node_index", "1");

    _smallConfig.reset(new vdstestlib::DirConfig(*_config));
    _node.reset(new TestServiceLayerApp(DiskCount(diskCount), NodeIndex(1),
                                        _config->getConfigId()));
    _testdoctype1 = _node->getTypeRepo()->getDocumentType("testdoctype1");
}

// Default provider setup which should work out of the box for most tests.
void
FileStorTestFixture::setUp()
{
    setupDisks(1);
    _node->setPersistenceProvider(
            spi::PersistenceProvider::UP(
                    new spi::dummy::DummyPersistence(_node->getTypeRepo(), 1)));
}

void
FileStorTestFixture::tearDown()
{
    _node.reset(0);
}

void
FileStorTestFixture::createBucket(const document::BucketId& bid)
{
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    _node->getPersistenceProvider().createBucket(
            makeSpiBucket(bid), context);

    StorBucketDatabase::WrappedEntry entry(
            _node->getStorageBucketDatabase().get(bid, "foo",
                    StorBucketDatabase::CREATE_IF_NONEXISTING));
    entry->disk = 0;
    entry->info = api::BucketInfo(0, 0, 0, 0, 0, true, false);
    entry.write();
}

bool
FileStorTestFixture::bucketExistsInDb(const document::BucketId& bucket) const
{
    StorBucketDatabase::WrappedEntry entry(
            _node->getStorageBucketDatabase().get(bucket, "bucketExistsInDb"));
    return entry.exist();
}

FileStorTestFixture::TestFileStorComponents::TestFileStorComponents(
        FileStorTestFixture& fixture,
        const char* testName,
        const StorageLinkInjector& injector)
    : _testName(testName),
      _fixture(fixture),
      manager(new FileStorManager(fixture._config->getConfigId(),
                                  fixture._node->getPartitions(),
                                  fixture._node->getPersistenceProvider(),
                                  fixture._node->getComponentRegister()))
{
    injector.inject(top);
    top.push_back(StorageLink::UP(manager));
    top.open();
}

api::StorageMessageAddress
FileStorTestFixture::TestFileStorComponents::makeSelfAddress() const {
    return api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 0);
}

void
FileStorTestFixture::TestFileStorComponents::sendDummyGet(
        const document::BucketId& bid)
{
    std::ostringstream id;
    id << "id:foo:testdoctype1:n=" << bid.getId() << ":0";
    std::shared_ptr<api::GetCommand> cmd(
            new api::GetCommand(makeDocumentBucket(bid), document::DocumentId(id.str()), "[all]"));
    cmd->setAddress(makeSelfAddress());
    cmd->setPriority(255);
    top.sendDown(cmd);
}

void
FileStorTestFixture::TestFileStorComponents::sendDummyGetDiff(
        const document::BucketId& bid)
{
    std::vector<api::GetBucketDiffCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    std::shared_ptr<api::GetBucketDiffCommand> cmd(
            new api::GetBucketDiffCommand(makeDocumentBucket(bid), nodes, 12345));
    cmd->setAddress(makeSelfAddress());
    cmd->setPriority(255);
    top.sendDown(cmd);
}

void
FileStorTestFixture::TestFileStorComponents::sendPut(
        const document::BucketId& bid,
        uint32_t docIdx,
        uint64_t timestamp)
{
    std::ostringstream id;
    id << "id:foo:testdoctype1:n=" << bid.getId() << ":" << docIdx;
    document::Document::SP doc(
            _fixture._node->getTestDocMan().createDocument("foobar", id.str()));
    std::shared_ptr<api::PutCommand> cmd(
            new api::PutCommand(makeDocumentBucket(bid), doc, timestamp));
    cmd->setAddress(makeSelfAddress());
    top.sendDown(cmd);
}

void 
FileStorTestFixture::setClusterState(const std::string& state)
{
    _node->getStateUpdater().setClusterState(
            lib::ClusterState::CSP(new lib::ClusterState(state)));
}


} // ns storage
