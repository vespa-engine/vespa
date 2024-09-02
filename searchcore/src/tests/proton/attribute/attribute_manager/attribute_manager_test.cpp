// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_manager_initializer.h>
#include <vespa/searchcore/proton/attribute/attribute_manager_reconfig.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/attribute/sequential_attributes_initializer.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <vespa/searchcore/proton/initializer/task_runner.h>
#include <vespa/searchcore/proton/server/executor_thread_service.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/test/attribute_vectors.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/searchcommon/attribute/i_attribute_functor.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/foregroundtaskexecutor.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/config-attributes.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_manager_test");

using std::string;
using namespace vespa::config::search;
using namespace config;
using namespace document;
using namespace proton;
using namespace search;
using namespace search::index;
using proton::initializer::InitializerTask;
using proton::test::AttributeUtils;
using proton::test::createInt32Attribute;
using proton::test::Int32Attribute;
using vespalib::ForegroundTaskExecutor;
using vespalib::ForegroundThreadExecutor;
using search::TuneFileAttributes;
using search::attribute::BasicType;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVector;
using search::attribute::ImportedAttributeVectorFactory;
using search::attribute::ReferenceAttribute;
using search::attribute::test::MockGidToLidMapperFactory;
using search::index::DummyFileHeaderContext;
using search::predicate::PredicateIndex;
using search::predicate::PredicateTreeAnnotations;
using search::test::DirectoryHandler;
using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespalib::eval::ValueType;
using vespalib::HwInfo;

using AVConfig = search::attribute::Config;
using AttrSpecList = proton::AttributeCollectionSpec::AttributeList;
using AttrMgrSpec = proton::AttributeCollectionSpec;

namespace fs = std::filesystem;

namespace {

const uint64_t createSerialNum = 42u;

class MyAttributeFunctor : public search::attribute::IConstAttributeFunctor
{
    std::vector<std::string> _names;

public:
    void
    operator()(const search::attribute::IAttributeVector &attributeVector) override {
        _names.push_back(attributeVector.getName());
    }

    std::string getSortedNames() {
        std::ostringstream os;
        std::sort(_names.begin(), _names.end());
        for (const std::string &name : _names) {
            if (!os.str().empty())
                os << ",";
            os << name;
        }
        return os.str();
    }
};

}

const string test_dir = "test_output";
const AVConfig & INT32_SINGLE = AttributeUtils::getInt32Config();
const AVConfig & INT32_ARRAY = AttributeUtils::getInt32ArrayConfig();

void
fillAttribute(const AttributeVector::SP &attr, uint32_t numDocs, int64_t value, uint64_t lastSyncToken)
{
    AttributeUtils::fillAttribute(*attr, numDocs, value, lastSyncToken);
}

void
fillAttribute(const AttributeVector::SP &attr, uint32_t from, uint32_t to, int64_t value, uint64_t lastSyncToken)
{
    AttributeUtils::fillAttribute(*attr, from, to, value, lastSyncToken);
}

search::SerialNum get_create_serial_num(const AttributeManager &am, const std::string &name)
{
    auto guard = am.getAttribute(name);
    if (!guard || !guard->valid()) {
        return 0;
    } else {
        return (*guard)->getCreateSerialNum();
    }
}

struct ImportedAttributesRepoBuilder {
    ImportedAttributesRepo::UP _repo;
    ImportedAttributesRepoBuilder() : _repo(std::make_unique<ImportedAttributesRepo>()) {}
    void add(const std::string &name) {
        auto refAttr = std::make_shared<ReferenceAttribute>(name + "_ref");
        refAttr->setGidToLidMapperFactory(std::make_shared<MockGidToLidMapperFactory>());
        auto targetAttr = search::AttributeFactory::createAttribute(name + "_target", INT32_SINGLE);
        auto documentMetaStore = std::shared_ptr<search::IDocumentMetaStoreContext>();
        auto targetDocumentMetaStore = std::make_shared<const DocumentMetaStoreContext>(std::make_shared<bucketdb::BucketDBOwner>());
        auto importedAttr = ImportedAttributeVectorFactory::create(name, refAttr, documentMetaStore, targetAttr, targetDocumentMetaStore, false);
        _repo->add(name, importedAttr);
    }
    ImportedAttributesRepo::UP build() {
        return std::move(_repo);
    }
};

struct BaseFixture
{
    DirectoryHandler _dirHandler;
    DummyFileHeaderContext _fileHeaderContext;
    ForegroundTaskExecutor _attributeFieldWriter;
    ForegroundThreadExecutor _shared;
    HwInfo                 _hwInfo;
    BaseFixture();
    ~BaseFixture();
    proton::AttributeManager::SP make_manager() {
        return std::make_shared<proton::AttributeManager>(test_dir, "test.subdb", TuneFileAttributes(),
                                                          _fileHeaderContext, std::make_shared<search::attribute::Interlock>(),
                                                          _attributeFieldWriter, _shared, _hwInfo);
    }
};

BaseFixture::BaseFixture()
    : _dirHandler(test_dir),
      _fileHeaderContext(),
      _attributeFieldWriter(),
      _shared(),
      _hwInfo()
{
}
BaseFixture::~BaseFixture() = default;

struct AttributeManagerFixture
{
    proton::AttributeManager::SP _msp;
    proton::AttributeManager &_m;
    ImportedAttributesRepoBuilder _builder;
    explicit AttributeManagerFixture(BaseFixture &bf);
    ~AttributeManagerFixture();
    AttributeVector::SP addAttribute(const std::string &name) {
        return _m.addAttribute({name, INT32_SINGLE}, createSerialNum);
    }
    void addImportedAttribute(const std::string &name) {
        _builder.add(name);
    }
    void setImportedAttributes() {
        _m.setImportedAttributes(_builder.build());
    }
};

AttributeManagerFixture::AttributeManagerFixture(BaseFixture &bf)
    : _msp(bf.make_manager()),
      _m(*_msp),
      _builder()
{}
AttributeManagerFixture::~AttributeManagerFixture() = default;

struct Fixture : public BaseFixture, public AttributeManagerFixture
{
    Fixture()
        : BaseFixture(),
          AttributeManagerFixture(*static_cast<BaseFixture *>(this))
    {
    }
};

struct SequentialAttributeManager
{
    SequentialAttributesInitializer initializer;
    uint32_t                 docid_limit;
    SerialNum                serial_num;
    proton::AttributeManager mgr;
    SequentialAttributeManager(const AttributeManager &currMgr, AttrMgrSpec && newSpec);
    ~SequentialAttributeManager();
};

SequentialAttributeManager::SequentialAttributeManager(const AttributeManager &currMgr, AttrMgrSpec && newSpec)
    : initializer(newSpec.getDocIdLimit()),
      docid_limit(newSpec.getDocIdLimit()),
      serial_num(newSpec.getCurrentSerialNum().value_or(0)),
      mgr(currMgr, std::move(newSpec), initializer)
{
    mgr.addInitializedAttributes(initializer.getInitializedAttributes(), docid_limit, serial_num);
}
SequentialAttributeManager::~SequentialAttributeManager() = default;

struct DummyInitializerTask : public InitializerTask
{
    void run() override {}
};

struct ParallelAttributeManager
{
    InitializerTask::SP documentMetaStoreInitTask;
    std::shared_ptr<bucketdb::BucketDBOwner> bucketDbOwner;
    std::shared_ptr<DocumentMetaStore> documentMetaStore;
    AllocStrategy        alloc_strategy;
    bool fastAccessAttributesOnly;
    std::shared_ptr<AttributeManager::SP> mgr;
    vespalib::ThreadStackExecutor masterExecutor;
    ExecutorThreadService master;
    AttributeManagerInitializer::SP initializer;

    ParallelAttributeManager(search::SerialNum configSerialNum, AttributeManager & baseAttrMgr,
                             const AttributesConfig &attrCfg, uint32_t docIdLimit);
    ~ParallelAttributeManager();
};

ParallelAttributeManager::ParallelAttributeManager(search::SerialNum configSerialNum, AttributeManager & baseAttrMgr,
                                                   const AttributesConfig &attrCfg, uint32_t docIdLimit)
    : documentMetaStoreInitTask(std::make_shared<DummyInitializerTask>()),
      bucketDbOwner(std::make_shared<bucketdb::BucketDBOwner>()),
      documentMetaStore(std::make_shared<DocumentMetaStore>(bucketDbOwner)),
      alloc_strategy(),
      fastAccessAttributesOnly(false),
      mgr(std::make_shared<AttributeManager::SP>()),
      masterExecutor(1),
      master(masterExecutor),
      initializer(std::make_shared<AttributeManagerInitializer>(configSerialNum, documentMetaStoreInitTask,
                                                                documentMetaStore, baseAttrMgr, attrCfg,
                                                                alloc_strategy,
                                                                fastAccessAttributesOnly, master, mgr))
{
    documentMetaStore->setCommittedDocIdLimit(docIdLimit);
    vespalib::ThreadStackExecutor executor(3);
    initializer::TaskRunner taskRunner(executor);
    taskRunner.runTask(initializer);
}
ParallelAttributeManager::~ParallelAttributeManager() = default;

class AttributeManagerTest : public ::testing::Test {
protected:
    AttributeManagerTest();
    ~AttributeManagerTest() override;
    static void SetUpTestSuite();
};

AttributeManagerTest::AttributeManagerTest() = default;
AttributeManagerTest::~AttributeManagerTest() = default;

void
AttributeManagerTest::SetUpTestSuite()
{
    fs::remove_all(fs::path(test_dir));
}

TEST_F(AttributeManagerTest, require_that_attributes_are_added)
{
    Fixture f;
    EXPECT_TRUE(f.addAttribute("a1").get() != nullptr);
    EXPECT_TRUE(f.addAttribute("a2").get() != nullptr);
    EXPECT_EQ("a1", (*f._m.getAttribute("a1"))->getName());
    EXPECT_EQ("a1", (*f._m.getAttributeReadGuard("a1", true))->getName());
    EXPECT_EQ("a2", (*f._m.getAttribute("a2"))->getName());
    EXPECT_EQ("a2", (*f._m.getAttributeReadGuard("a2", true))->getName());
    EXPECT_TRUE(!f._m.getAttribute("not")->valid());

    auto rv = f._m.readable_attribute_vector("a1");
    ASSERT_TRUE(rv.get() != nullptr);
    EXPECT_EQ("a1", rv->makeReadGuard(true)->attribute()->getName());

    rv = f._m.readable_attribute_vector("a2");
    ASSERT_TRUE(rv.get() != nullptr);
    EXPECT_EQ("a2", rv->makeReadGuard(true)->attribute()->getName());

    EXPECT_TRUE(f._m.readable_attribute_vector("not_valid").get() == nullptr);
}

TEST_F(AttributeManagerTest, require_that_predicate_attributes_are_added)
{
    Fixture f;
    EXPECT_TRUE(f._m.addAttribute({"p1", AttributeUtils::getPredicateConfig()},
                                  createSerialNum).get() != nullptr);
    EXPECT_EQ("p1", (*f._m.getAttribute("p1"))->getName());
    EXPECT_EQ("p1", (*f._m.getAttributeReadGuard("p1", true))->getName());
}

TEST_F(AttributeManagerTest, require_that_attributes_are_flushed_and_loaded)
{
    BaseFixture f;
    IndexMetaInfo ia1(test_dir + "/a1");
    IndexMetaInfo ia2(test_dir + "/a2");
    IndexMetaInfo ia3(test_dir + "/a3");
    {
        AttributeManagerFixture amf(f);
        proton::AttributeManager &am = amf._m;
        AttributeVector::SP a1 = amf.addAttribute("a1");
        EXPECT_EQ(1u, a1->getNumDocs()); // Resized to size of attributemanager
        fillAttribute(a1, 1, 3, 2, 100);
        EXPECT_EQ(3u, a1->getNumDocs()); // Resized to size of attributemanager
        AttributeVector::SP a2 = amf.addAttribute("a2");
        EXPECT_EQ(1u, a2->getNumDocs()); // Not resized to size of attributemanager
        fillAttribute(a2, 1, 5, 4, 100);
        EXPECT_EQ(5u, a2->getNumDocs()); // Increased
        EXPECT_TRUE(!ia1.load());
        EXPECT_TRUE(!ia2.load());
        EXPECT_TRUE(!ia3.load());
        am.flushAll(0);
        EXPECT_TRUE(ia1.load());
        EXPECT_EQ(100u, ia1.getBestSnapshot().syncToken);
        EXPECT_TRUE(ia2.load());
        EXPECT_EQ(100u, ia2.getBestSnapshot().syncToken);
    }
    {
        AttributeManagerFixture amf(f);
        proton::AttributeManager &am = amf._m;
        AttributeVector::SP a1 = amf.addAttribute("a1"); // loaded

        EXPECT_EQ(3u, a1->getNumDocs());
        fillAttribute(a1, 1, 2, 200);
        EXPECT_EQ(4u, a1->getNumDocs());
        AttributeVector::SP a2 = amf.addAttribute("a2"); // loaded
        {
            AttributeWriter aw(amf._msp);

            EXPECT_EQ(5u, a2->getNumDocs());
            EXPECT_EQ(4u, a1->getNumDocs());
            aw.onReplayDone(5u);
            EXPECT_EQ(5u, a2->getNumDocs());
            EXPECT_EQ(5u, a1->getNumDocs());
            fillAttribute(a2, 1, 4, 200);
            EXPECT_EQ(6u, a2->getNumDocs());
        }
        AttributeVector::SP a3 = amf.addAttribute("a3"); // not-loaded
        AttributeWriter aw(amf._msp);
        EXPECT_EQ(1u, a3->getNumDocs());
        aw.onReplayDone(6);
        EXPECT_EQ(6u, a3->getNumDocs());
        fillAttribute(a3, 1, 7, 6, 200);
        EXPECT_EQ(7u, a3->getNumDocs());
        EXPECT_TRUE(ia1.load());
        EXPECT_EQ(100u, ia1.getBestSnapshot().syncToken);
        EXPECT_TRUE(ia2.load());
        EXPECT_EQ(100u, ia2.getBestSnapshot().syncToken);
        EXPECT_TRUE(!ia3.load());
        am.flushAll(0);
        EXPECT_TRUE(ia1.load());
        EXPECT_EQ(200u, ia1.getBestSnapshot().syncToken);
        EXPECT_TRUE(ia2.load());
        EXPECT_EQ(200u, ia2.getBestSnapshot().syncToken);
        EXPECT_TRUE(ia3.load());
        EXPECT_EQ(200u, ia3.getBestSnapshot().syncToken);
    }
    {
        AttributeManagerFixture amf(f);
        AttributeVector::SP a1 = amf.addAttribute("a1"); // loaded
        EXPECT_EQ(6u, a1->getNumDocs());
        AttributeVector::SP a2 = amf.addAttribute("a2"); // loaded
        EXPECT_EQ(6u, a1->getNumDocs());
        EXPECT_EQ(6u, a2->getNumDocs());
        AttributeVector::SP a3 = amf.addAttribute("a3"); // loaded
        AttributeWriter aw(amf._msp);
        EXPECT_EQ(6u, a1->getNumDocs());
        EXPECT_EQ(6u, a2->getNumDocs());
        EXPECT_EQ(7u, a3->getNumDocs());
        aw.onReplayDone(7);
        EXPECT_EQ(7u, a1->getNumDocs());
        EXPECT_EQ(7u, a2->getNumDocs());
        EXPECT_EQ(7u, a3->getNumDocs());
    }
}

TEST_F(AttributeManagerTest, require_that_predicate_attributes_are_flushed_and_loaded)
{
    BaseFixture f;
    IndexMetaInfo ia1(test_dir + "/a1");
    {
        AttributeManagerFixture amf(f);
        proton::AttributeManager &am = amf._m;
        AttributeVector::SP a1 = am.addAttribute({"a1", AttributeUtils::getPredicateConfig()}, createSerialNum);
        EXPECT_EQ(1u, a1->getNumDocs());

        auto &pa = dynamic_cast<PredicateAttribute &>(*a1);
        PredicateIndex &index = pa.getIndex();
        uint32_t doc_id;
        a1->addDoc(doc_id);
        index.indexEmptyDocument(doc_id);
        pa.commit(CommitParam(100));

        EXPECT_EQ(2u, a1->getNumDocs());

        EXPECT_TRUE(!ia1.load());
        am.flushAll(0);
        EXPECT_TRUE(ia1.load());
        EXPECT_EQ(100u, ia1.getBestSnapshot().syncToken);
    }
    {
        AttributeManagerFixture amf(f);
        proton::AttributeManager &am = amf._m;
        AttributeVector::SP a1 = am.addAttribute({"a1", AttributeUtils::getPredicateConfig()}, createSerialNum); // loaded
        EXPECT_EQ(2u, a1->getNumDocs());

        auto &pa = dynamic_cast<PredicateAttribute &>(*a1);
        PredicateIndex &index = pa.getIndex();
        uint32_t doc_id;
        a1->addDoc(doc_id);
        PredicateTreeAnnotations annotations(3);
        annotations.interval_map[123] = {{ 0x0001ffff }};
        index.indexDocument(1, annotations);
        pa.commit(CommitParam(200));

        EXPECT_EQ(3u, a1->getNumDocs());
        EXPECT_TRUE(ia1.load());
        EXPECT_EQ(100u, ia1.getBestSnapshot().syncToken);
        am.flushAll(0);
        EXPECT_TRUE(ia1.load());
        EXPECT_EQ(200u, ia1.getBestSnapshot().syncToken);
    }
}

TEST_F(AttributeManagerTest, require_that_extra_attribute_is_added)
{
    Fixture f;
    f._m.addExtraAttribute(createInt32Attribute("extra"));
    AttributeGuard::UP exguard(f._m.getAttribute("extra"));
    EXPECT_TRUE(dynamic_cast<Int32Attribute *>(exguard->operator->()) != nullptr);
}

TEST_F(AttributeManagerTest, require_that_reconfig_can_add_attributes)
{
    Fixture f;
    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP ex(createInt32Attribute("ex"));
    f._m.addExtraAttribute(ex);

    AttrSpecList newSpec;
    newSpec.emplace_back("a1", INT32_SINGLE);
    newSpec.emplace_back("a2", INT32_SINGLE);
    newSpec.emplace_back("a3", INT32_SINGLE);

    SequentialAttributeManager sam(f._m, AttrMgrSpec(std::move(newSpec), f._m.getNumDocs(), 10));
    std::vector<AttributeGuard> list;
    sam.mgr.getAttributeList(list);
    std::sort(list.begin(), list.end(), [](const AttributeGuard & a, const AttributeGuard & b) {
        return a->getName() < b->getName();
    });
    EXPECT_EQ(3u, list.size());
    EXPECT_EQ("a1", list[0]->getName());
    EXPECT_TRUE(list[0].operator->() == a1.get()); // reuse
    EXPECT_EQ("a2", list[1]->getName());
    EXPECT_EQ("a3", list[2]->getName());
    EXPECT_TRUE(sam.mgr.getAttribute("ex")->operator->() == ex.get()); // reuse
}

TEST_F(AttributeManagerTest, require_that_reconfig_can_remove_attributes)
{
    Fixture f;
    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP a2 = f.addAttribute("a2");
    AttributeVector::SP a3 = f.addAttribute("a3");

    AttrSpecList newSpec;
    newSpec.emplace_back("a2", INT32_SINGLE);

    SequentialAttributeManager sam(f._m, AttrMgrSpec(std::move(newSpec), 1, 10));
    std::vector<AttributeGuard> list;
    sam.mgr.getAttributeList(list);
    EXPECT_EQ(1u, list.size());
    EXPECT_EQ("a2", list[0]->getName());
    EXPECT_TRUE(list[0].operator->() == a2.get()); // reuse
}

TEST_F(AttributeManagerTest, require_that_new_attributes_after_reconfig_are_initialized)
{
    Fixture f;
    AttributeVector::SP a1 = f.addAttribute("a1");
    uint32_t docId(0);
    a1->addDoc(docId);
    EXPECT_EQ(1u, docId);
    a1->addDoc(docId);
    EXPECT_EQ(2u, docId);
    EXPECT_EQ(3u, a1->getNumDocs());

    AttrSpecList newSpec;
    newSpec.emplace_back("a1", INT32_SINGLE);
    newSpec.emplace_back("a2", INT32_SINGLE);
    newSpec.emplace_back("a3", INT32_ARRAY);

    SequentialAttributeManager sam(f._m, AttrMgrSpec(std::move(newSpec), 3, 4));
    AttributeGuard::UP a2ap = sam.mgr.getAttribute("a2");
    AttributeGuard &a2(*a2ap);
    EXPECT_EQ(3u, a2->getNumDocs());
    EXPECT_TRUE(search::attribute::isUndefined<int32_t>(a2->getInt(1)));
    EXPECT_TRUE(search::attribute::isUndefined<int32_t>(a2->getInt(2)));
    EXPECT_EQ(0u, a2->getStatus().getLastSyncToken());
    AttributeGuard::UP a3ap = sam.mgr.getAttribute("a3");
    AttributeGuard &a3(*a3ap);
    AttributeVector::largeint_t buf[1];
    EXPECT_EQ(3u, a3->getNumDocs());
    EXPECT_EQ(0u, a3->get(1, buf, 1));
    EXPECT_EQ(0u, a3->get(2, buf, 1));
    EXPECT_EQ(0u, a3->getStatus().getLastSyncToken());
}

TEST_F(AttributeManagerTest, require_that_removed_attributes_cannot_resurrect)
{
    BaseFixture f;
    auto am1 = f.make_manager();
    {
        AttributeVector::SP a1 = am1->addAttribute({"a1", INT32_SINGLE}, 0);
        fillAttribute(a1, 2, 10, 15);
        EXPECT_EQ(3u, a1->getNumDocs());
    }

    AttrSpecList ns1;
    SequentialAttributeManager am2(*am1, AttrMgrSpec(std::move(ns1), 3, 16));
    am1.reset();

    AttrSpecList ns2;
    ns2.emplace_back("a1", INT32_SINGLE);
    // 2 new documents added since a1 was removed
    SequentialAttributeManager am3(am2.mgr, AttrMgrSpec(std::move(ns2), 5, 20));

    AttributeGuard::UP ag1ap = am3.mgr.getAttribute("a1");
    AttributeGuard &ag1(*ag1ap);
    ASSERT_TRUE(ag1.valid());
    EXPECT_EQ(5u, ag1->getNumDocs());
    EXPECT_TRUE(search::attribute::isUndefined<int32_t>(ag1->getInt(1)));
    EXPECT_TRUE(search::attribute::isUndefined<int32_t>(ag1->getInt(2)));
    EXPECT_TRUE(search::attribute::isUndefined<int32_t>(ag1->getInt(3)));
    EXPECT_TRUE(search::attribute::isUndefined<int32_t>(ag1->getInt(4)));
    EXPECT_EQ(0u, ag1->getStatus().getLastSyncToken());
}

TEST_F(AttributeManagerTest, require_that_extra_attribute_is_not_treated_as_removed)
{
    Fixture f;
    AttributeVector::SP ex(createInt32Attribute("ex"));
    f._m.addExtraAttribute(ex);
    ex->commit(CommitParam(1));

    AttrSpecList ns;
    SequentialAttributeManager am2(f._m, AttrMgrSpec(std::move(ns), 2, 1));
    EXPECT_TRUE(am2.mgr.getAttribute("ex")->operator->() == ex.get()); // reuse
}

TEST_F(AttributeManagerTest, require_that_removed_fields_can_be_pruned)
{
    Fixture f;
    f.addAttribute("a1");
    f.addAttribute("a2");
    f.addAttribute("a3");
    f._m.flushAll(10);

    AttrSpecList newSpec;
    newSpec.emplace_back("a2", INT32_SINGLE);
    SequentialAttributeManager sam(f._m, AttrMgrSpec(std::move(newSpec), 1, 11));
    sam.mgr.pruneRemovedFields(11);

    EXPECT_FALSE(fs::exists(fs::path(test_dir + "/a1")));
    EXPECT_TRUE(fs::exists(fs::path(test_dir + "/a2")));
    EXPECT_FALSE(fs::exists(fs::path(test_dir + "/a3")));
}

TEST_F(AttributeManagerTest, require_that_lid_space_can_be_compacted)
{
    Fixture f;
    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP a2 = f.addAttribute("a2");
    AttributeVector::SP ex(createInt32Attribute("ex"));
    f._m.addExtraAttribute(ex);
    AttributeWriter aw(f._msp);
    const int64_t attrValue = 33;
    fillAttribute(a1, 20, attrValue, 100);
    fillAttribute(a2, 20, attrValue, 100);
    fillAttribute(ex, 20, attrValue, 100);

    EXPECT_EQ(21u, a1->getNumDocs());
    EXPECT_EQ(21u, a2->getNumDocs());
    EXPECT_EQ(20u, ex->getNumDocs());
    EXPECT_EQ(21u, a1->getCommittedDocIdLimit());
    EXPECT_EQ(21u, a2->getCommittedDocIdLimit());
    EXPECT_EQ(20u, ex->getCommittedDocIdLimit());

    aw.compactLidSpace(10, 101);

    EXPECT_EQ(21u, a1->getNumDocs());
    EXPECT_EQ(21u, a2->getNumDocs());
    EXPECT_EQ(20u, ex->getNumDocs());
    EXPECT_EQ(10u, a1->getCommittedDocIdLimit());
    EXPECT_EQ(10u, a2->getCommittedDocIdLimit());
    EXPECT_EQ(20u, ex->getCommittedDocIdLimit());
}

TEST_F(AttributeManagerTest, require_that_lid_space_compaction_op_can_be_ignored)
{
    Fixture f;
    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP a2 = f.addAttribute("a2");
    AttributeVector::SP ex(createInt32Attribute("ex"));
    f._m.addExtraAttribute(ex);
    AttributeWriter aw(f._msp);
    const int64_t attrValue = 33;
    fillAttribute(a1, 20, attrValue, 200);
    fillAttribute(a2, 20, attrValue, 100);
    fillAttribute(ex, 20, attrValue, 100);

    EXPECT_EQ(21u, a1->getNumDocs());
    EXPECT_EQ(21u, a2->getNumDocs());
    EXPECT_EQ(20u, ex->getNumDocs());
    EXPECT_EQ(21u, a1->getCommittedDocIdLimit());
    EXPECT_EQ(21u, a2->getCommittedDocIdLimit());
    EXPECT_EQ(20u, ex->getCommittedDocIdLimit());

    aw.compactLidSpace(10, 101);

    EXPECT_EQ(21u, a1->getNumDocs());
    EXPECT_EQ(21u, a2->getNumDocs());
    EXPECT_EQ(20u, ex->getNumDocs());
    EXPECT_EQ(21u, a1->getCommittedDocIdLimit());
    EXPECT_EQ(10u, a2->getCommittedDocIdLimit());
    EXPECT_EQ(20u, ex->getCommittedDocIdLimit());
}

TEST_F(AttributeManagerTest, require_that_flushed_serial_number_can_be_retrieved)
{
    Fixture f;
    f.addAttribute("a1");
    EXPECT_EQ(0u, f._m.getFlushedSerialNum("a1"));
    f._m.flushAll(100);
    EXPECT_EQ(100u, f._m.getFlushedSerialNum("a1"));
    EXPECT_EQ(0u, f._m.getFlushedSerialNum("a2"));
}


TEST_F(AttributeManagerTest, require_that_writable_attributes_can_be_retrieved)
{
    Fixture f;
    auto a1 = f.addAttribute("a1");
    auto a2 = f.addAttribute("a2");
    AttributeVector::SP ex(createInt32Attribute("ex"));
    f._m.addExtraAttribute(ex);
    auto &vec = f._m.getWritableAttributes();
    EXPECT_EQ(2u, vec.size());
    EXPECT_EQ(a1.get(), vec[0]);
    EXPECT_EQ(a2.get(), vec[1]);
    EXPECT_EQ(a1.get(), f._m.getWritableAttribute("a1"));
    EXPECT_EQ(a2.get(), f._m.getWritableAttribute("a2"));
    AttributeVector *noAttr = nullptr;
    EXPECT_EQ(noAttr, f._m.getWritableAttribute("a3"));
    EXPECT_EQ(noAttr, f._m.getWritableAttribute("ex"));
}


void
populateAndFlushAttributes(AttributeManagerFixture &f)
{
    const int64_t attrValue = 7;
    AttributeVector::SP a1 = f.addAttribute("a1");
    fillAttribute(a1, 1, 10, attrValue, createSerialNum);
    AttributeVector::SP a2 = f.addAttribute("a2");
    fillAttribute(a2, 1, 10, attrValue, createSerialNum);
    AttributeVector::SP a3 = f.addAttribute("a3");
    fillAttribute(a3, 1, 10, attrValue, createSerialNum);
    f._m.flushAll(createSerialNum + 10);
}

void
validateAttribute(const AttributeVector &attr)
{
    SCOPED_TRACE(attr.getName());
    ASSERT_EQ(10u, attr.getNumDocs());
    EXPECT_EQ(createSerialNum + 10, attr.getStatus().getLastSyncToken());
    for (uint32_t docId = 1; docId < 10; ++docId) {
        EXPECT_EQ(7, attr.getInt(docId));
    }
}

TEST_F(AttributeManagerTest, require_that_attributes_can_be_initialized_and_loaded_in_sequence)
{
    BaseFixture f;
    {
        AttributeManagerFixture amf(f);
        populateAndFlushAttributes(amf);
    }
    {
        AttributeManagerFixture amf(f);

        AttrSpecList newSpec;
        newSpec.emplace_back("a1", INT32_SINGLE);
        newSpec.emplace_back("a2", INT32_SINGLE);
        newSpec.emplace_back("a3", INT32_SINGLE);

        SequentialAttributeManager newMgr(amf._m, AttrMgrSpec(std::move(newSpec), 10, createSerialNum + 5));

        AttributeGuard::UP a1 = newMgr.mgr.getAttribute("a1");
        validateAttribute(*a1->get());
        AttributeGuard::UP a2 = newMgr.mgr.getAttribute("a2");
        validateAttribute(*a2->get());
        AttributeGuard::UP a3 = newMgr.mgr.getAttribute("a3");
        validateAttribute(*a3->get());
    }
}

AttributesConfigBuilder::Attribute
createAttributeConfig(const std::string &name)
{
    AttributesConfigBuilder::Attribute result;
    result.name = name;
    result.datatype = AttributesConfigBuilder::Attribute::Datatype::INT32;
    result.collectiontype = AttributesConfigBuilder::Attribute::Collectiontype::SINGLE;
    return result;
}

TEST_F(AttributeManagerTest, require_that_attributes_can_be_initialized_and_loaded_in_parallel)
{
    BaseFixture f;
    {
        AttributeManagerFixture amf(f);
        populateAndFlushAttributes(amf);
    }
    {
        AttributeManagerFixture amf(f);

        AttributesConfigBuilder attrCfg;
        attrCfg.attribute.push_back(createAttributeConfig("a1"));
        attrCfg.attribute.push_back(createAttributeConfig("a2"));
        attrCfg.attribute.push_back(createAttributeConfig("a3"));

        ParallelAttributeManager newMgr(createSerialNum + 5, *amf._msp, attrCfg, 10);

        AttributeGuard::UP a1 = newMgr.mgr->get()->getAttribute("a1");
        validateAttribute(*a1->get());
        AttributeGuard::UP a2 = newMgr.mgr->get()->getAttribute("a2");
        validateAttribute(*a2->get());
        AttributeGuard::UP a3 = newMgr.mgr->get()->getAttribute("a3");
        validateAttribute(*a3->get());
    }
}

TEST_F(AttributeManagerTest, require_that_we_can_call_functions_on_all_attributes_via_functor)
{
    Fixture f;
    f.addAttribute("a1");
    f.addAttribute("a2");
    f.addAttribute("a3");
    std::shared_ptr<MyAttributeFunctor> functor =
        std::make_shared<MyAttributeFunctor>();
    f._m.asyncForEachAttribute(functor);
    EXPECT_EQ("a1,a2,a3", functor->getSortedNames());
}

TEST_F(AttributeManagerTest, require_that_imported_attributes_are_exposed_via_attribute_context_together_with_regular_attributes)
{
    Fixture f;
    f.addAttribute("attr");
    f.addImportedAttribute("imported");
    f.setImportedAttributes();

    IAttributeContext::UP ctx = f._m.createContext();
    EXPECT_TRUE(ctx->getAttribute("attr") != nullptr);
    EXPECT_TRUE(ctx->getAttribute("imported") != nullptr);
    EXPECT_TRUE(ctx->getAttribute("not_found") == nullptr);
    EXPECT_TRUE(ctx->getAttributeStableEnum("attr") != nullptr);
    EXPECT_TRUE(ctx->getAttributeStableEnum("imported") != nullptr);
    EXPECT_TRUE(ctx->getAttributeStableEnum("not_found") == nullptr);

    std::vector<const IAttributeVector *> all;
    ctx->getAttributeList(all);
    EXPECT_EQ(2u, all.size());
    EXPECT_EQ("attr", all[0]->getName());
    EXPECT_EQ("imported", all[1]->getName());
}

TEST_F(AttributeManagerTest, imported_attributes_are_transparently_returned_from_readable_attribute_vector)
{
    Fixture f;
    f.addAttribute("attr");
    f.addImportedAttribute("imported");
    f.setImportedAttributes();
    auto av = f._m.readable_attribute_vector("imported");
    ASSERT_TRUE(av);
    auto g = av->makeReadGuard(false);
    EXPECT_EQ("imported", g->attribute()->getName());
}

TEST_F(AttributeManagerTest, require_that_attribute_vector_of_wrong_type_is_dropped)
{
    BaseFixture f;
    AVConfig generic_tensor(BasicType::TENSOR);
    generic_tensor.setTensorType(ValueType::from_spec("tensor(x{})"));
    AVConfig dense_tensor(BasicType::TENSOR);
    dense_tensor.setTensorType(ValueType::from_spec("tensor(x[10])"));
    AVConfig predicate(BasicType::PREDICATE);
    using PredicateParams = search::attribute::PredicateParams;
    PredicateParams predicateParams;
    predicateParams.setArity(2);
    predicate.setPredicateParams(predicateParams);
    AVConfig predicate2(BasicType::PREDICATE);
    PredicateParams predicateParams2;
    predicateParams2.setArity(4);
    predicate2.setPredicateParams(predicateParams2);

    auto am1 = f.make_manager();
    am1->addAttribute({"a1", INT32_SINGLE}, 1);
    am1->addAttribute({"a2", INT32_SINGLE}, 2);
    am1->addAttribute({"a3", generic_tensor}, 3);
    am1->addAttribute({"a4", generic_tensor}, 4);
    am1->addAttribute({"a5", predicate}, 5);
    am1->addAttribute({"a6", predicate}, 6);
    AttrSpecList newSpec;
    newSpec.emplace_back("a1", INT32_SINGLE);
    newSpec.emplace_back("a2", INT32_ARRAY);
    newSpec.emplace_back("a3", generic_tensor);
    newSpec.emplace_back("a4", dense_tensor);
    newSpec.emplace_back("a5", predicate);
    newSpec.emplace_back("a6", predicate2);
    SequentialAttributeManager am2(*am1, AttrMgrSpec(std::move(newSpec), 5, 20));
    EXPECT_EQ(1, get_create_serial_num(*am1, "a1"));
    EXPECT_EQ(2, get_create_serial_num(*am1, "a2"));
    EXPECT_EQ(3, get_create_serial_num(*am1, "a3"));
    EXPECT_EQ(4, get_create_serial_num(*am1, "a4"));
    EXPECT_EQ(5, get_create_serial_num(*am1, "a5"));
    EXPECT_EQ(6, get_create_serial_num(*am1, "a6"));
    EXPECT_EQ(1, get_create_serial_num(am2.mgr, "a1"));
    EXPECT_EQ(20, get_create_serial_num(am2.mgr, "a2"));
    EXPECT_EQ(3, get_create_serial_num(am2.mgr, "a3"));
    EXPECT_EQ(20, get_create_serial_num(am2.mgr, "a4"));
    EXPECT_EQ(5, get_create_serial_num(am2.mgr, "a5"));
    EXPECT_EQ(20, get_create_serial_num(am2.mgr, "a6"));
}

search::SerialNum get_shrink_target_serial(proton::AttributeManager &mgr, const std::string &name)
{
    auto shrinker = mgr.getShrinker(name);
    return shrinker->getFlushedSerialNum();
}

TEST_F(AttributeManagerTest, require_that_we_can_guess_flushed_serial_number_for_shrink_flushtarget)
{
    BaseFixture f;
    auto am1 = f.make_manager();
    am1->addAttribute({"a1", INT32_SINGLE}, 1);
    am1->addAttribute({"a2", INT32_SINGLE}, 2);
    EXPECT_EQ(0, get_shrink_target_serial(*am1, "a1"));
    EXPECT_EQ(1, get_shrink_target_serial(*am1, "a2"));
    am1->flushAll(10);
    am1 = f.make_manager();
    am1->addAttribute({"a1", INT32_SINGLE}, 1);
    am1->addAttribute({"a2", INT32_SINGLE}, 2);
    EXPECT_EQ(10, get_shrink_target_serial(*am1, "a1"));
    EXPECT_EQ(10, get_shrink_target_serial(*am1, "a2"));
}

TEST_F(AttributeManagerTest, require_that_shrink_flushtarget_is_handed_over_to_new_attribute_manager)
{
    BaseFixture f;
    auto am1 = f.make_manager();
    am1->addAttribute({"a1", INT32_SINGLE}, 4);
    AttrSpecList newSpec;
    newSpec.emplace_back("a1", INT32_SINGLE);
    auto am2 = am1->prepare_create(AttrMgrSpec(std::move(newSpec), 5, 20))->create(5, 20);
    auto am3 = std::dynamic_pointer_cast<AttributeManager>(am2);
    EXPECT_EQ(3, get_shrink_target_serial(*am3, "a1"));
    EXPECT_EQ(am1->getShrinker("a1"), am3->getShrinker("a1"));
}

TEST_F(AttributeManagerTest, transient_resource_usage_is_zero_in_steady_state)
{
    Fixture f;
    f.addAttribute("a1");
    f.addAttribute("a2");
    auto usage = f._m.get_transient_resource_usage();
    EXPECT_EQ(0u, usage.disk());
    EXPECT_EQ(0u, usage.memory());
}

TEST_F(AttributeManagerTest, late_create_serial_number_is_set_on_new_attributes)
{
    Fixture f;
    auto am1 = f.make_manager();
    am1->addAttribute({"a1", INT32_SINGLE}, 4);
    auto a1 = am1->getAttribute("a1")->getSP();
    uint32_t docid = 0;
    a1->addDoc(docid);
    EXPECT_EQ(1u, docid);
    a1->clearDoc(docid);
    a1->commit(CommitParam(5));
    AttrSpecList new_spec;
    new_spec.emplace_back("a1", INT32_SINGLE);
    new_spec.emplace_back("a2", INT32_SINGLE);
    // late serial number
    auto am2 = am1->prepare_create(AttrMgrSpec(std::move(new_spec), 10, std::nullopt))->create(14, 20);
    auto am3 = std::dynamic_pointer_cast<AttributeManager>(am2);
    EXPECT_TRUE(a1 == am3->getAttribute("a1")->getSP());
    auto a2 = am3->getAttribute("a2")->getSP();
    EXPECT_EQ(4, get_create_serial_num(*am3, "a1"));
    EXPECT_EQ(20, get_create_serial_num(*am3, "a2"));
    EXPECT_EQ(3, get_shrink_target_serial(*am3, "a1"));
    EXPECT_EQ(19, get_shrink_target_serial(*am3, "a2"));
    EXPECT_EQ(0u, am3->getFlushedSerialNum("a1"));
    EXPECT_EQ(0u, am3->getFlushedSerialNum("a2"));
    EXPECT_EQ(2u, a1->getNumDocs());
    EXPECT_EQ(2u, a1->getCommittedDocIdLimit());
    EXPECT_EQ(14u, a2->getNumDocs());
    EXPECT_EQ(14u, a2->getCommittedDocIdLimit());
}

GTEST_MAIN_RUN_ALL_TESTS()
