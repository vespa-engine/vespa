// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_registrator.h>
#include <vespa/searchcore/proton/test/mock_gid_to_lid_change_handler.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <map>
#include <vespa/log/log.h>
LOG_SETUP("gid_to_lid_change_registrator_test");

namespace proton {

class MyListener : public IGidToLidChangeListener
{
    vespalib::string _docTypeName;
    vespalib::string _name;
public:
    MyListener(const vespalib::string &docTypeName, const vespalib::string &name)
        : _docTypeName(docTypeName),
          _name(name)
    {
    }
    ~MyListener() override { }
    void notifyPutDone(IDestructorCallbackSP, document::GlobalId, uint32_t) override { }
    void notifyRemove(IDestructorCallbackSP, document::GlobalId) override { }
    void notifyRegistered(const std::vector<document::GlobalId>&) override { }
    const vespalib::string &getName() const override { return _name; }
    const vespalib::string &getDocTypeName() const override { return _docTypeName; }
};



using test::MockGidToLidChangeHandler;
using AddEntry = MockGidToLidChangeHandler::AddEntry;
using RemoveEntry = MockGidToLidChangeHandler::RemoveEntry;

struct Fixture
{
    std::shared_ptr<MockGidToLidChangeHandler> _handler;

    Fixture()
        : _handler(std::make_shared<MockGidToLidChangeHandler>())
    {
    }

    ~Fixture() { }

    std::unique_ptr<GidToLidChangeRegistrator>
    getRegistrator(const vespalib::string &docTypeName) {
        return std::make_unique<GidToLidChangeRegistrator>(_handler, docTypeName);
    }

    void assertAdds(const std::vector<AddEntry> &expAdds) {
        _handler->assertAdds(expAdds);
    }

    void assertRemoves(const std::vector<RemoveEntry> &expRemoves) {
        _handler->assertRemoves(expRemoves);
    }
};

TEST_F("Test that we can register a listener", Fixture)
{
    auto registrator = f.getRegistrator("testdoc");
    TEST_DO(f.assertAdds({}));
    TEST_DO(f.assertRemoves({}));
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f1"));
    TEST_DO(f.assertAdds({{"testdoc","f1"}}));
    TEST_DO(f.assertRemoves({}));
    registrator.reset();
    TEST_DO(f.assertAdds({{"testdoc","f1"}}));
    TEST_DO(f.assertRemoves({{"testdoc",{"f1"}}}));
}

TEST_F("Test that we can register multiple listeners", Fixture)
{
    auto registrator = f.getRegistrator("testdoc");
    TEST_DO(f.assertAdds({}));
    TEST_DO(f.assertRemoves({}));
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f1"));
    registrator->addListener(std::make_unique<MyListener>("testdoc", "f2"));
    TEST_DO(f.assertAdds({{"testdoc","f1"},{"testdoc","f2"}}));
    TEST_DO(f.assertRemoves({}));
    registrator.reset();
    TEST_DO(f.assertAdds({{"testdoc","f1"},{"testdoc","f2"}}));
    TEST_DO(f.assertRemoves({{"testdoc",{"f1","f2"}}}));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
