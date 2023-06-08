// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/messages.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/persistence/spi/test.h>
#include <sstream>

using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;

namespace storage {

const uint32_t FileStorTestFixture::MSG_WAIT_TIME;

void
FileStorTestFixture::setupPersistenceThreads(uint32_t threads)
{
    std::string rootOfRoot = "todo-make-unique-filestorefixture";
    _config = std::make_unique<vdstestlib::DirConfig>(getStandardConfig(true, rootOfRoot));
    _config->getConfig("stor-server").set("root_folder", (rootOfRoot + "-vdsroot.2"));
    _config->getConfig("stor-devices").set("root_folder", (rootOfRoot + "-vdsroot.2"));
    _config->getConfig("stor-server").set("node_index", "1");
    _config->getConfig("stor-filestor").set("num_threads", std::to_string(threads));

    _node = std::make_unique<TestServiceLayerApp>(NodeIndex(1), _config->getConfigId());
    _testdoctype1 = _node->getTypeRepo()->getDocumentType("testdoctype1");
}

// Default provider setup which should work out of the box for most tests.
void
FileStorTestFixture::SetUp()
{
    setupPersistenceThreads(1);
    _node->setPersistenceProvider(std::make_unique<spi::dummy::DummyPersistence>(_node->getTypeRepo()));
    _node->getPersistenceProvider().initialize();
}

void
FileStorTestFixture::TearDown()
{
    _node.reset();
}

void
FileStorTestFixture::createBucket(const document::BucketId& bid)
{
    _node->getPersistenceProvider().createBucket(makeSpiBucket(bid));
    StorBucketDatabase::WrappedEntry entry(
            _node->getStorageBucketDatabase().get(bid, "foo", StorBucketDatabase::CREATE_IF_NONEXISTING));
    entry->info = api::BucketInfo(0, 0, 0, 0, 0, true, false);
    entry.write();
}

bool
FileStorTestFixture::bucketExistsInDb(const document::BucketId& bucket) const
{
    StorBucketDatabase::WrappedEntry entry(_node->getStorageBucketDatabase().get(bucket, "bucketExistsInDb"));
    return entry.exists();
}

FileStorTestFixture::TestFileStorComponents::TestFileStorComponents(
        FileStorTestFixture& fixture,
        const StorageLinkInjector& injector)
    : _fixture(fixture),
      top(),
      manager(nullptr)
{
    injector.inject(top);
    auto fsm = std::make_unique<FileStorManager>(config::ConfigUri(fixture._config->getConfigId()), fixture._node->getPersistenceProvider(),
                                                 fixture._node->getComponentRegister(), *fixture._node, fixture._node->get_host_info());
    manager = fsm.get();
    top.push_back(std::move(fsm));
    top.open();
}

vespalib::string _Storage("storage");

api::StorageMessageAddress
FileStorTestFixture::makeSelfAddress() {
    return api::StorageMessageAddress(&_Storage, lib::NodeType::STORAGE, 0);
}

void
FileStorTestFixture::TestFileStorComponents::sendDummyGet(const document::BucketId& bid)
{
    std::ostringstream id;
    id << "id:foo:testdoctype1:n=" << bid.getId() << ":0";
    auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(bid), document::DocumentId(id.str()), document::AllFields::NAME);
    cmd->setAddress(makeSelfAddress());
    cmd->setPriority(255);
    top.sendDown(cmd);
}

void
FileStorTestFixture::TestFileStorComponents::sendDummyGetDiff(const document::BucketId& bid)
{
    std::vector<api::GetBucketDiffCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    auto cmd = std::make_shared<api::GetBucketDiffCommand>(makeDocumentBucket(bid), nodes, 12345);
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
    document::Document::SP doc(_fixture._node->getTestDocMan().createDocument("foobar", id.str()));
    auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, timestamp);
    cmd->setAddress(makeSelfAddress());
    top.sendDown(cmd);
}

void 
FileStorTestFixture::setClusterState(const std::string& state)
{
    _node->getStateUpdater().setClusterState(std::make_shared<lib::ClusterState>(state));
}

} // ns storage
