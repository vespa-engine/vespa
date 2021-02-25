// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_manager_explorer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/test/attribute_vectors.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/foregroundtaskexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("attributes_state_explorer_test");

using namespace proton;
using namespace proton::test;
using search::AttributeVector;
using vespalib::ForegroundTaskExecutor;
using vespalib::ForegroundThreadExecutor;
using search::TuneFileAttributes;
using search::index::DummyFileHeaderContext;
using search::test::DirectoryHandler;

const vespalib::string TEST_DIR = "test_output";

struct Fixture
{
    DirectoryHandler _dirHandler;
    DummyFileHeaderContext _fileHeaderContext;
    ForegroundTaskExecutor _attributeFieldWriter;
    ForegroundThreadExecutor _shared;
    HwInfo                 _hwInfo;
    AttributeManager::SP _mgr;
    AttributeManagerExplorer _explorer;
    Fixture()
        : _dirHandler(TEST_DIR),
          _fileHeaderContext(),
          _attributeFieldWriter(),
          _shared(),
          _hwInfo(),
          _mgr(new AttributeManager(TEST_DIR, "test.subdb", TuneFileAttributes(),
                                    _fileHeaderContext,
                                    _attributeFieldWriter,
                                    _shared,
                                    _hwInfo)),
          _explorer(_mgr)
    {
        addAttribute("regular");
        addExtraAttribute("extra");
    }
    void addAttribute(const vespalib::string &name) {
        _mgr->addAttribute({name, AttributeUtils::getInt32Config()}, 1);
    }
    void addExtraAttribute(const vespalib::string &name) {
        _mgr->addExtraAttribute(createInt32Attribute(name));
    }
};

typedef std::vector<vespalib::string> StringVector;

TEST_F("require that attributes are exposed as children names", Fixture)
{
    StringVector children = f._explorer.get_children_names();
    std::sort(children.begin(), children.end());
    EXPECT_EQUAL(StringVector({"extra", "regular"}), children);
}

TEST_F("require that attributes are explorable", Fixture)
{
    EXPECT_TRUE(f._explorer.get_child("regular").get() != nullptr);
    EXPECT_TRUE(f._explorer.get_child("extra").get() != nullptr);
    EXPECT_TRUE(f._explorer.get_child("not").get() == nullptr);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
