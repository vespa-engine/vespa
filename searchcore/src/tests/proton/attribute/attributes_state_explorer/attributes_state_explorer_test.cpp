// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_manager_explorer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/test/attribute_vectors.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("attributes_state_explorer_test");

using namespace proton;
using namespace proton::test;
using search::AttributeVector;
using search::DictionaryConfig;
using vespalib::ForegroundThreadExecutor;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using vespalib::Slime;
using search::TuneFileAttributes;
using search::index::DummyFileHeaderContext;
using search::test::DirectoryHandler;

const vespalib::string TEST_DIR = "test_output";

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
    AttributeManager::SP _mgr;
    AttributeManagerExplorer _explorer;
    AttributesStateExplorerTest()
        : _dirHandler(TEST_DIR),
          _fileHeaderContext(),
          _attribute_field_writer(SequencedTaskExecutor::create(test_executor, 1)),
          _shared(),
          _hwInfo(),
          _mgr(new AttributeManager(TEST_DIR, "test.subdb", TuneFileAttributes(),
                                    _fileHeaderContext,
                                    std::make_shared<search::attribute::Interlock>(),
                                    *_attribute_field_writer,
                                    _shared,
                                    _hwInfo)),
          _explorer(_mgr)
    {
        addAttribute("regular");
        addExtraAttribute("extra");
        add_fast_search_attribute("btree", DictionaryConfig::Type::BTREE);
        add_fast_search_attribute("hybrid", DictionaryConfig::Type::BTREE_AND_HASH);
        add_fast_search_attribute("hash", DictionaryConfig::Type::HASH);
    }
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

};

typedef std::vector<vespalib::string> StringVector;

TEST_F(AttributesStateExplorerTest, require_that_attributes_are_exposed_as_children_names)
{
    StringVector children = _explorer.get_children_names();
    std::sort(children.begin(), children.end());
    EXPECT_EQ(StringVector({"btree", "extra", "hash", "hybrid", "regular"}), children);
}

TEST_F(AttributesStateExplorerTest, require_that_attributes_are_explorable)
{
    EXPECT_TRUE(_explorer.get_child("regular").get() != nullptr);
    EXPECT_TRUE(_explorer.get_child("extra").get() != nullptr);
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

GTEST_MAIN_RUN_ALL_TESTS()
