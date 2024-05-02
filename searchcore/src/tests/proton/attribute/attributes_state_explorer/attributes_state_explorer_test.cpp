// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_manager_explorer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/test/attribute_vectors.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("attributes_state_explorer_test");

using namespace proton;
using namespace proton::test;
using search::AttributeVector;
using search::DictionaryConfig;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::ImportedAttributeVector;
using search::attribute::ImportedAttributeVectorFactory;
using search::attribute::ReferenceAttribute;
using search::attribute::test::MockGidToLidMapperFactory;
using vespalib::ForegroundThreadExecutor;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using vespalib::Slime;
using search::TuneFileAttributes;
using search::index::DummyFileHeaderContext;
using search::test::DirectoryHandler;
using vespalib::HwInfo;

const vespalib::string TEST_DIR = "test_output";

const vespalib::string ref_name("ref");
const vespalib::string target_name("f3");
const vespalib::string imported_name("my_f3");

namespace {
VESPA_THREAD_STACK_TAG(test_executor)
}

struct AttributesStateExplorerTest : public ::testing::Test
{
    DirectoryHandler _dirHandler;
    DummyFileHeaderContext _fileHeaderContext;
    std::unique_ptr<ISequencedTaskExecutor> _attribute_field_writer;
    ForegroundThreadExecutor _shared;
    HwInfo                 _hwInfo;
    std::shared_ptr<const IDocumentMetaStoreContext> _parent_dms;
    std::shared_ptr<IDocumentMetaStoreContext> _dms;
    std::shared_ptr<AttributeManager> _parent_mgr;
    AttributeManager::SP _mgr;
    AttributeManagerExplorer _explorer;
    AttributesStateExplorerTest() noexcept;
    ~AttributesStateExplorerTest() override;
    void addAttribute(const vespalib::string &name) {
        _mgr->addAttribute({name, AttributeUtils::getInt32Config()}, 1);
    }
    void add_fast_search_attribute(const vespalib::string &name,
                                   DictionaryConfig::Type dictionary_type) {
        search::attribute::Config cfg = AttributeUtils::getInt32Config();
        cfg.setFastSearch(true);
        cfg.set_dictionary_config(search::DictionaryConfig(dictionary_type));
        _mgr->addAttribute({name, cfg}, 1);
    }
    void addExtraAttribute(const vespalib::string &name) {
        _mgr->addExtraAttribute(createInt32Attribute(name));
    }
    Slime explore_attribute(const vespalib::string &name) {
        Slime result;
        vespalib::slime::SlimeInserter inserter(result);
        _explorer.get_child(name)->get_state(inserter, true);
        return result;
    }
    void add_reference_attribute() {
        search::attribute::Config cfg(BasicType::REFERENCE);
        _mgr->addAttribute({ ref_name, cfg }, 1);
        auto& ref_attr = dynamic_cast<ReferenceAttribute&>(**_mgr->getAttribute(ref_name));
        ref_attr.setGidToLidMapperFactory(std::make_shared<MockGidToLidMapperFactory>());
    }
    std::shared_ptr<ReferenceAttribute> get_reference_attribute() {
        return std::dynamic_pointer_cast<ReferenceAttribute>(_mgr->getAttribute(ref_name)->getSP());
    }
    void add_imported_attributes() {
        auto repo = std::make_unique<ImportedAttributesRepo>();
        auto attr = ImportedAttributeVectorFactory::create(imported_name,
                                                           get_reference_attribute(),
                                                           _dms,
                                                           _parent_mgr->getAttribute(target_name)->getSP(),
                                                           _parent_dms,
                                                           false);
        repo->add(imported_name, attr);
        _mgr->setImportedAttributes(std::move(repo));
    }
};

AttributesStateExplorerTest::AttributesStateExplorerTest() noexcept
    : _dirHandler(TEST_DIR),
      _fileHeaderContext(),
      _attribute_field_writer(SequencedTaskExecutor::create(test_executor, 1)),
      _shared(),
      _hwInfo(),
      _parent_dms(std::make_shared<const DocumentMetaStoreContext>(std::make_shared<bucketdb::BucketDBOwner>())),
      _dms(),
      _parent_mgr(std::make_shared<AttributeManager>
                  (TEST_DIR, "test.parent.subdb", TuneFileAttributes(),
                   _fileHeaderContext,
                   std::make_shared<search::attribute::Interlock>(),
                   *_attribute_field_writer,
                   _shared,
                   _hwInfo)),
      _mgr(new AttributeManager(TEST_DIR, "test.subdb", TuneFileAttributes(),
                                _fileHeaderContext,
                                std::make_shared<search::attribute::Interlock>(),
                                *_attribute_field_writer,
                                _shared,
                                _hwInfo)),
      _explorer(_mgr)
{
    _parent_mgr->addAttribute({target_name, AttributeUtils::getInt32Config()}, 1);
    addAttribute("regular");
    addExtraAttribute("extra");
    add_fast_search_attribute("btree", DictionaryConfig::Type::BTREE);
    add_fast_search_attribute("hybrid", DictionaryConfig::Type::BTREE_AND_HASH);
    add_fast_search_attribute("hash", DictionaryConfig::Type::HASH);
    add_reference_attribute();
    add_imported_attributes();
}

AttributesStateExplorerTest::~AttributesStateExplorerTest() = default;


using StringVector = std::vector<vespalib::string>;

TEST_F(AttributesStateExplorerTest, require_that_attributes_are_exposed_as_children_names)
{
    StringVector children = _explorer.get_children_names();
    std::sort(children.begin(), children.end());
    EXPECT_EQ(StringVector({"btree", "hash", "hybrid", "my_f3", "ref", "regular"}), children);
}

TEST_F(AttributesStateExplorerTest, require_that_attributes_are_explorable)
{
    EXPECT_TRUE(_explorer.get_child("regular").get() != nullptr);
    EXPECT_TRUE(_explorer.get_child("extra").get() == nullptr);
    EXPECT_TRUE(_explorer.get_child("not").get() == nullptr);
}

TEST_F(AttributesStateExplorerTest, require_that_dictionary_memory_usage_is_reported)
{
    {
        auto slime = explore_attribute("btree");
        auto& dictionary = slime.get()["enumStore"]["dictionary"];
        EXPECT_LT(0, dictionary["btreeMemoryUsage"]["used"].asLong());
        EXPECT_EQ(0, dictionary["hashMemoryUsage"]["used"].asLong());
    }
    {
        auto slime = explore_attribute("hash");
        auto& dictionary = slime.get()["enumStore"]["dictionary"];
        EXPECT_EQ(0, dictionary["btreeMemoryUsage"]["used"].asLong());
        EXPECT_LT(0, dictionary["hashMemoryUsage"]["used"].asLong());
    }
    {
        auto slime = explore_attribute("hybrid");
        auto& dictionary = slime.get()["enumStore"]["dictionary"];
        EXPECT_LT(0, dictionary["btreeMemoryUsage"]["used"].asLong());
        EXPECT_LT(0, dictionary["hashMemoryUsage"]["used"].asLong());
    }
}

TEST_F(AttributesStateExplorerTest, require_that_imported_attribute_shows_memory_usage)
{
    vespalib::string cache_memory_usage("cacheMemoryUsage");
    auto slime = explore_attribute(imported_name);
    EXPECT_LT(0, slime[cache_memory_usage]["allocated"].asLong());
    EXPECT_LT(0, slime[cache_memory_usage]["used"].asLong());
}

GTEST_MAIN_RUN_ALL_TESTS()
