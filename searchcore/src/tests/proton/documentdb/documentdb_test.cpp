// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/proton/common/dummydbowner.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fastos/file.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/searchcore/proton/attribute/flushableattribute.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/searchcore/proton/docsummary/summaryflushtarget.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreflushtarget.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchcore/proton/flushengine/threadedflushtarget.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/job_tracked_flush_target.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/document_db_explorer.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcorespi/index/indexflushtarget.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace cloud::config::filedistribution;
using namespace proton;
using namespace vespalib::slime;

using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumenttypesConfig;
using document::test::makeBucketSpace;
using search::TuneFileDocumentDB;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::transactionlog::TransLogServer;
using searchcorespi::IFlushTarget;
using searchcorespi::index::IndexFlushTarget;
using vespa::config::search::core::ProtonConfig;
using vespa::config::content::core::BucketspacesConfig;
using vespalib::Slime;

namespace {

struct MyDBOwner : public DummyDBOwner
{
    std::shared_ptr<DocumentDBReferenceRegistry> _registry;
    MyDBOwner();
    ~MyDBOwner() override;
    std::shared_ptr<IDocumentDBReferenceRegistry> getDocumentDBReferenceRegistry() const override {
        return _registry;
    }
};

MyDBOwner::MyDBOwner()
    : DummyDBOwner(),
      _registry(std::make_shared<DocumentDBReferenceRegistry>())
{}
MyDBOwner::~MyDBOwner() = default;

struct Fixture {
    DummyWireService _dummy;
    MyDBOwner _myDBOwner;
    vespalib::ThreadStackExecutor _summaryExecutor;
    HwInfo _hwInfo;
    DocumentDB::SP _db;
    DummyFileHeaderContext _fileHeaderContext;
    TransLogServer _tls;
    matching::QueryLimiter _queryLimiter;
    vespalib::Clock _clock;

    Fixture();
    ~Fixture();
};

Fixture::Fixture()
    : _dummy(),
      _myDBOwner(),
      _summaryExecutor(8, 128*1024),
      _hwInfo(),
      _db(),
      _fileHeaderContext(),
      _tls("tmp", 9014, ".", _fileHeaderContext),
      _queryLimiter(),
      _clock()
{
    DocumentDBConfig::DocumenttypesConfigSP documenttypesConfig(new DocumenttypesConfig());
    DocumentType docType("typea", 0);
    std::shared_ptr<const DocumentTypeRepo> repo(new DocumentTypeRepo(docType));
    TuneFileDocumentDB::SP tuneFileDocumentDB(new TuneFileDocumentDB);
    config::DirSpec spec(TEST_PATH("cfg"));
    DocumentDBConfigHelper mgr(spec, "typea");
    BootstrapConfig::SP
        b(new BootstrapConfig(1, documenttypesConfig, repo,
                              std::make_shared<ProtonConfig>(),
                              std::make_shared<FiledistributorrpcConfig>(),
                              std::make_shared<BucketspacesConfig>(),
                              tuneFileDocumentDB, HwInfo()));
    mgr.forwardConfig(b);
    mgr.nextGeneration(0);
    _db.reset(new DocumentDB(".", mgr.getConfig(), "tcp/localhost:9014", _queryLimiter, _clock, DocTypeName("typea"),
                             makeBucketSpace(),
                             *b->getProtonConfigSP(), _myDBOwner, _summaryExecutor, _summaryExecutor, _tls, _dummy,
                             _fileHeaderContext, ConfigStore::UP(new MemoryConfigStore),
                             std::make_shared<vespalib::ThreadStackExecutor>(16, 128 * 1024), _hwInfo));
    _db->start();
    _db->waitForOnlineState();
}

Fixture::~Fixture() = default;

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

TEST_F("requireThatIndexFlushTargetIsUsed", Fixture) {
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

TEST_F("requireThatFlushTargetsAreNamedBySubDocumentDB", Fixture) {
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    for (const IFlushTarget::SP & target : f._db->getFlushTargets()) {
        vespalib::string name = target->getName();
        EXPECT_TRUE((name.find("0.ready.") == 0) ||
                    (name.find("1.removed.") == 0) ||
                    (name.find("2.notready.") == 0));
    }
}

TEST_F("requireThatAttributeFlushTargetsAreUsed", Fixture) {
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    size_t numAttrs = getNumTargets<FlushableAttribute>(targets);
    // attr1 defined in attributes.cfg
    EXPECT_EQUAL(1u, numAttrs);
}

TEST_F("requireThatDocumentMetaStoreFlushTargetIsUsed", Fixture) {
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    size_t numMetaStores = getNumTargets<DocumentMetaStoreFlushTarget>(targets);
    EXPECT_EQUAL(3u, numMetaStores);
}

TEST_F("requireThatSummaryFlushTargetsIsUsed", Fixture) {
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    size_t num = getNumTargets<SummaryFlushTarget>(targets);
    EXPECT_EQUAL(3u, num);
}

TEST_F("require that shrink lid space flush targets are created", Fixture) {
    auto targets = f._db->getFlushTargets();
    ASSERT_TRUE(!targets.empty());
    size_t num = getNumTargets<ShrinkLidSpaceFlushTarget>(targets);
    // 1x attribute, 3x document meta store, 3x document store
    EXPECT_EQUAL(1u + 3u + 3u, num);
}

TEST_F("requireThatCorrectStatusIsReported", Fixture) {
    StatusReport::UP report(f._db->reportStatus());
    EXPECT_EQUAL("documentdb:typea", report->getComponent());
    EXPECT_EQUAL(StatusReport::UPOK, report->getState());
    EXPECT_EQUAL("", report->getMessage());
}

TEST_F("requireThatStateIsReported", Fixture)
{
    Slime slime;
    SlimeInserter inserter(slime);
    DocumentDBExplorer(f._db).get_state(inserter, false);

    EXPECT_EQUAL(
            "{\n"
            "    \"documentType\": \"typea\",\n"
            "    \"status\": {\n"
            "        \"state\": \"ONLINE\",\n"
            "        \"configState\": \"OK\"\n"
            "    },\n"
            "    \"documents\": {\n"
            "        \"active\": 0,\n"
            "        \"indexed\": 0,\n"
            "        \"stored\": 0,\n"
            "        \"removed\": 0\n"
            "    }\n"
            "}\n",
            slime.toString());
}

TEST_F("require that session manager can be explored", Fixture)
{
    EXPECT_TRUE(DocumentDBExplorer(f._db).get_child("session"));
}

TEST_F("require that document db registers reference", Fixture)
{
    auto &registry = f._myDBOwner._registry;
    auto reference = registry->get("typea");
    EXPECT_TRUE(reference);
    auto attr = reference->getAttribute("attr1");
    EXPECT_TRUE(attr);
    EXPECT_EQUAL(search::attribute::BasicType::INT32, attr->getBasicType());
}

}  // namespace

TEST_MAIN() {
    DummyFileHeaderContext::setCreator("documentdb_test");
    FastOS_File::MakeDirectory("typea");
    TEST_RUN_ALL();
    FastOS_FileInterface::EmptyAndRemoveDirectory("typea");
}
