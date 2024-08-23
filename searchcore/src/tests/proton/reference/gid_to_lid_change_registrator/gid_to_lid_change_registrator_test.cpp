// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/globalid.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_registrator.h>
#include <vespa/searchcore/proton/test/mock_gid_to_lid_change_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <map>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_change_registrator_test");

namespace proton {

class MyListener : public IGidToLidChangeListener
{
    std::string _docTypeName;
    std::string _name;
public:
    MyListener(const std::string &docTypeName, const std::string &name)
        : _docTypeName(docTypeName),
          _name(name)
    {
    }
    ~MyListener() override { }
    void notifyPutDone(IDestructorCallbackSP, document::GlobalId, uint32_t) override { }
    void notifyRemove(IDestructorCallbackSP, document::GlobalId) override { }
    void notifyRegistered(const std::vector<document::GlobalId>&) override { }
    const std::string &getName() const override { return _name; }
    const std::string &getDocTypeName() const override { return _docTypeName; }
};



using test::MockGidToLidChangeHandler;
using AddEntry = MockGidToLidChangeHandler::AddEntry;
using RemoveEntry = MockGidToLidChangeHandler::RemoveEntry;

using AddVector = std::vector<AddEntry>;
using RemoveVector = std::vector<RemoveEntry>;

class GidToLidChangeRregistratorTest : public ::testing::Test
{
    std::shared_ptr<MockGidToLidChangeHandler> _handler;

protected:
    GidToLidChangeRregistratorTest();
    ~GidToLidChangeRregistratorTest() override;

    std::unique_ptr<GidToLidChangeRegistrator>
    getRegistrator(const std::string &docTypeName) {
        return std::make_unique<GidToLidChangeRegistrator>(_handler, docTypeName);
    }

    const AddVector& get_adds() const noexcept { return _handler->get_adds(); }
    const RemoveVector& get_removes() const noexcept { return _handler->get_removes(); }
};

GidToLidChangeRregistratorTest::GidToLidChangeRregistratorTest()
    : ::testing::Test(),
      _handler(std::make_shared<MockGidToLidChangeHandler>())
{
}

GidToLidChangeRregistratorTest::~GidToLidChangeRregistratorTest() = default;

TEST_F(GidToLidChangeRregistratorTest, we_can_register_a_listener)
{
    auto registrator = getRegistrator("testdoc");
    EXPECT_EQ(AddVector{}, get_adds());
    EXPECT_EQ(RemoveVector{}, get_removes());
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f1"));
    EXPECT_EQ((AddVector{{"testdoc","f1"}}), get_adds());
    EXPECT_EQ(RemoveVector{}, get_removes());
    registrator.reset();
    EXPECT_EQ((AddVector{{"testdoc","f1"}}), get_adds());
    EXPECT_EQ((RemoveVector{{"testdoc",{"f1"}}}), get_removes());
}

TEST_F(GidToLidChangeRregistratorTest, we_can_register_multiple_listeners)
{
    auto registrator = getRegistrator("testdoc");
    EXPECT_EQ(AddVector{}, get_adds());
    EXPECT_EQ(RemoveVector{}, get_removes());
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f1"));
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f2"));
    EXPECT_EQ((AddVector{{"testdoc","f1"},{"testdoc","f2"}}), get_adds());
    EXPECT_EQ(RemoveVector{}, get_removes());
    registrator.reset();
    EXPECT_EQ((AddVector{{"testdoc","f1"},{"testdoc","f2"}}), get_adds());
    EXPECT_EQ((RemoveVector{{"testdoc",{"f1","f2"}}}), get_removes());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
