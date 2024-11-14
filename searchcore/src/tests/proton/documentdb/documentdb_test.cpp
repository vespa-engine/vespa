// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/test/dummydbowner.h>
#include <vespa/searchcore/proton/attribute/flushableattribute.h>
#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/searchcore/proton/docsummary/summaryflushtarget.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreflushtarget.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchcore/proton/flushengine/threadedflushtarget.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/dummy_wire_service.h>
#include <vespa/searchcore/proton/metrics/job_tracked_flush_target.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_registry.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/document_db_explorer.h>
#include <vespa/searchcore/proton/server/documentdb.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/feedhandler.h>
#include <vespa/searchcore/proton/server/fileconfigmanager.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/test/mock_shared_threading_service.h>
#include <vespa/searchcore/proton/test/port_numbers.h>
#include <vespa/searchcorespi/index/indexflushtarget.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/fnet/transport.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <filesystem>
#include <iostream>

using namespace cloud::config::filedistribution;
using namespace proton;
using namespace vespalib::slime;
using namespace std::chrono_literals;

using document::DocumentType;
using document::DocumentTypeRepo;
using document::test::makeBucketSpace;
using search::SerialNum;
using search::TuneFileDocumentDB;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::transactionlog::TransLogServer;
using searchcorespi::IFlushTarget;
using searchcorespi::index::IndexFlushTarget;
using vespa::config::search::core::ProtonConfig;
using vespa::config::content::core::BucketspacesConfig;
using vespalib::HwInfo;
using vespalib::Slime;

namespace {
constexpr int tls_port = proton::test::port_numbers::documentdb_tls_port;

std::string tls_port_spec() {
    return vespalib::SocketSpec::from_host_port("localhost", tls_port).spec();
}

void
cleanup_dirs(bool file_config)
{
    std::filesystem::remove_all(std::filesystem::path("typea"));
    std::filesystem::remove_all(std::filesystem::path("tmp"));
    if (file_config) {
        std::filesystem::remove_all(std::filesystem::path("config"));
    }
}

std::string
config_subdir(SerialNum serialNum)
{
    vespalib::asciistream os;
    os << "config/config-" << serialNum;
    return os.str();
}

struct FixtureBase {
    bool _cleanup;
    bool _file_config;
    FixtureBase(bool file_config);
    ~FixtureBase();
    void disable_cleanup() { _cleanup = false; }
};

FixtureBase::FixtureBase(bool file_config)
    : _cleanup(true),
      _file_config(file_config)
{
    std::filesystem::create_directory(std::filesystem::path("typea"));
}


FixtureBase::~FixtureBase()
{
    if (_cleanup) {
        cleanup_dirs(_file_config);
    }
}

struct Fixture : public FixtureBase {
    DummyWireService _dummy;
    DummyDBOwner _myDBOwner;
    vespalib::ThreadStackExecutor _summaryExecutor;
    MockSharedThreadingService _shared_service;
    HwInfo _hwInfo;
    DocumentDB::SP _db;
    DummyFileHeaderContext _fileHeaderContext;
    TransLogServer _tls;
    matching::QueryLimiter _queryLimiter;

    std::unique_ptr<ConfigStore> make_config_store();
    Fixture();
    Fixture(bool file_config);
    ~Fixture();
};

Fixture::Fixture()
    : Fixture(false)
{
}

Fixture::Fixture(bool file_config)
    : FixtureBase(file_config),
      _dummy(),
      _myDBOwner(),
      _summaryExecutor(8),
      _shared_service(_summaryExecutor),
      _hwInfo(),
      _db(),
      _fileHeaderContext(),
      _tls(_shared_service.transport(), "tmp", tls_port, ".", _fileHeaderContext),
      _queryLimiter()
{
    auto documenttypesConfig = std::make_shared<DocumenttypesConfig>();
    DocumentType docType("typea", 0);
    auto repo = std::make_shared<DocumentTypeRepo>(docType);
    auto tuneFileDocumentDB = std::make_shared<TuneFileDocumentDB>();
    config::DirSpec spec(TEST_PATH("cfg"));
    DocumentDBConfigHelper mgr(spec, "typea");
    auto b = std::make_shared<BootstrapConfig>(1, documenttypesConfig, repo,
                                               std::make_shared<ProtonConfig>(),
                                               std::make_shared<FiledistributorrpcConfig>(),
                                               std::make_shared<BucketspacesConfig>(),
                                               tuneFileDocumentDB, HwInfo());
    mgr.forwardConfig(b);
    mgr.nextGeneration(_shared_service.transport(), 0ms);
    _db = DocumentDB::create(".", mgr.getConfig(), tls_port_spec(), _queryLimiter, DocTypeName("typea"),
                             makeBucketSpace(),
                             *b->getProtonConfigSP(), _myDBOwner, _shared_service, _tls, _dummy,
                             _fileHeaderContext,
                             std::make_shared<search::attribute::Interlock>(),
                             make_config_store(),
                             std::make_shared<vespalib::ThreadStackExecutor>(16), _hwInfo,
                             std::shared_ptr<search::diskindex::IPostingListCache>());
    _db->start();
    _db->waitForOnlineState();
}

Fixture::~Fixture()
{
    _db->close();
    _shared_service.transport().ShutDown(true);
}

std::unique_ptr<ConfigStore>
Fixture::make_config_store()
{
    if (_file_config) {
        return std::make_unique<FileConfigManager>(_shared_service.transport(), "config", "", "typea");
    } else {
        return std::make_unique<MemoryConfigStore>();
    }
}

const IFlushTarget *
extractRealFlushTarget(const IFlushTarget *target)
{
    const auto tracked = dynamic_cast<const JobTrackedFlushTarget*>(target);
    if (tracked != nullptr) {
        const auto threaded = dynamic_cast<const ThreadedFlushTarget*>(&tracked->getTarget());
        if (threaded != nullptr) {
            return threaded->getFlushTarget().get();
        }
    }
    return nullptr;
}

}

class DocumentDBTest : public ::testing::Test {
protected:
    DocumentDBTest();
    ~DocumentDBTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

DocumentDBTest::DocumentDBTest() = default;
DocumentDBTest::~DocumentDBTest() = default;

void
DocumentDBTest::SetUpTestSuite()
{
    cleanup_dirs(true);
    DummyFileHeaderContext::setCreator("documentdb_test");
}

void
DocumentDBTest::TearDownTestSuite()
{
    cleanup_dirs(true);
}

TEST_F(DocumentDBTest, requireThatIndexFlushTargetIsUsed)
{
    Fixture f;
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    const IndexFlushTarget *index = nullptr;
    for (size_t i = 0; i < targets.size(); ++i) {
        const IFlushTarget *target = extractRealFlushTarget(targets[i].get());
        if (target != nullptr) {
            index = dynamic_cast<const IndexFlushTarget *>(target);
        }
        if (index) {
            break;
        }
    }
    ASSERT_TRUE(index);
}

namespace {

template <typename Target>
size_t getNumTargets(const std::vector<IFlushTarget::SP> & targets)
{
    size_t retval = 0;
    for (const auto & candidate : targets) {
        const IFlushTarget *target = extractRealFlushTarget(candidate.get());
        if (dynamic_cast<const Target*>(target) == nullptr) {
            continue;
        }
        retval++;
    }
    return retval;
}

}

TEST_F(DocumentDBTest, requireThatFlushTargetsAreNamedBySubDocumentDB)
{
    Fixture f;
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    for (const IFlushTarget::SP & target : f._db->getFlushTargets()) {
        std::string name = target->getName();
        EXPECT_TRUE((name.find("0.ready.") == 0) ||
                    (name.find("1.removed.") == 0) ||
                    (name.find("2.notready.") == 0));
    }
}

TEST_F(DocumentDBTest, requireThatAttributeFlushTargetsAreUsed)
{
    Fixture f;
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    size_t numAttrs = getNumTargets<FlushableAttribute>(targets);
    // attr1 defined in attributes.cfg
    EXPECT_EQ(1u, numAttrs);
}

TEST_F(DocumentDBTest, requireThatDocumentMetaStoreFlushTargetIsUsed)
{
    Fixture f;
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    size_t numMetaStores = getNumTargets<DocumentMetaStoreFlushTarget>(targets);
    EXPECT_EQ(3u, numMetaStores);
}

TEST_F(DocumentDBTest, requireThatSummaryFlushTargetsIsUsed)
{
    Fixture f;
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    size_t num = getNumTargets<SummaryFlushTarget>(targets);
    EXPECT_EQ(3u, num);
}

TEST_F(DocumentDBTest, require_that_shrink_lid_space_flush_targets_are_created)
{
    Fixture f;
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    size_t num = getNumTargets<ShrinkLidSpaceFlushTarget>(targets);
    // 1x attribute, 3x document meta store, 3x document store
    EXPECT_EQ(1u + 3u + 3u, num);
}

TEST_F(DocumentDBTest, requireThatCorrectStatusIsReported)
{
    Fixture f;
    StatusReport::UP report(f._db->reportStatus());
    EXPECT_EQ("documentdb:typea", report->getComponent());
    EXPECT_EQ(StatusReport::UPOK, report->getState());
    EXPECT_EQ("", report->getMessage());
}

TEST_F(DocumentDBTest, requireThatStateIsReported)
{
    Fixture f;
    Slime slime;
    SlimeInserter inserter(slime);
    DocumentDBExplorer(f._db).get_state(inserter, false);

    EXPECT_EQ(
        "{\n"
        "    \"documentType\": \"typea\",\n"
        "    \"status\": {\n"
        "        \"state\": \"ONLINE\",\n"
        "        \"configState\": \"OK\"\n"
        "    },\n"
        "    \"documents\": {\n"
        "        \"active\": 0,\n"
        "        \"ready\": 0,\n"
        "        \"total\": 0,\n"
        "        \"removed\": 0\n"
        "    }\n"
        "}\n",
        slime.toString());
}

TEST_F(DocumentDBTest, require_that_document_db_registers_reference)
{
    Fixture f;
    auto &registry = f._myDBOwner._registry;
    auto reference = registry->get("typea");
    EXPECT_TRUE(reference);
    auto attr = reference->getAttribute("attr1");
    EXPECT_TRUE(attr);
    auto attrReadGuard = attr->makeReadGuard(false);
    EXPECT_EQ(search::attribute::BasicType::INT32, attrReadGuard->attribute()->getBasicType());
}

TEST_F(DocumentDBTest, require_that_normal_restart_works)
{
    {
        Fixture f(true);
        f.disable_cleanup();
    }
    {
        Fixture f(true);
    }
}

TEST_F(DocumentDBTest, require_that_resume_after_interrupted_save_config_works)
{
    SerialNum serialNum = 0;
    {
        Fixture f(true);
        f.disable_cleanup();
        serialNum = f._db->getFeedHandler().getSerialNum();
    }
    {
        /*
         * Simulate interrupted save config by copying best config to
         * serial number after end of transaction log
         */
        std::cout << "Replay end serial num is " << serialNum << std::endl;
        search::IndexMetaInfo info("config");
        ASSERT_TRUE(info.load());
        auto best_config_snapshot = info.getBestSnapshot();
        ASSERT_TRUE(best_config_snapshot.valid);
        std::cout << "Best config serial is " << best_config_snapshot.syncToken << std::endl;
        auto old_config_subdir = config_subdir(best_config_snapshot.syncToken);
        auto new_config_subdir = config_subdir(serialNum + 1);
        std::filesystem::copy(std::filesystem::path(old_config_subdir), std::filesystem::path(new_config_subdir));
        info.addSnapshot({true, serialNum + 1, new_config_subdir.substr(new_config_subdir.rfind('/') + 1)});
        info.save();
    }
    {
        Fixture f(true);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
